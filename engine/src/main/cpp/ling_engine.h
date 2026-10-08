// Ling-3.0-tiny 전용 추론 엔진 (llama.cpp 위의 얇은 계층, JNI와 독립 — 호스트에서도 빌드/테스트 가능)
//
// 핵심 기능
//  - 증분 prefill: 컨텍스트에 이미 들어있는 토큰열과 새 프롬프트의 최장 공통 접두부(LCP)만 재사용
//  - 체크포인트: KDA 재귀 상태는 되감을 수 없으므로 재귀 부분만 스냅샷(PARTIAL_ONLY, ~20MB)해 두고
//               편집/재생성 시 가장 가까운 스냅샷으로 복원 후 차이분만 prefill
//  - 생성: top_k -> top_p -> min_p -> temp -> dist 샘플러 체인, UTF-8 경계 보정 스트리밍
//  - 벤치: pp/tg 측정 (스레드/백엔드 자동 튜닝용)
#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

struct llama_model;
struct llama_context;
struct llama_vocab;
struct llama_sampler;
struct ggml_threadpool;

namespace ling {

using token = int32_t;

struct EngineParams {
    std::string model_path;
    int  n_ctx           = 16384;
    int  n_batch         = 512;
    int  n_ubatch        = 512;
    int  n_threads       = 4;  // decode
    int  n_threads_batch = 6;  // prefill
    // 쉼표 구분 CPU 번호 ("6,7,0,1"). 비어 있으면 OS 기본 배치
    std::string cpumask;
    bool strict_cpu      = false;
    int  poll            = 50;   // 0..100
    bool use_mmap        = true;
    bool use_mlock       = false;
    // CPU 가중치 재배열(repack, ARM i8mm/dotprod 고속 경로). x86 AMX 경로는 MLA 3D 가중치에서
    // 스케줄러 assert를 일으키므로(호스트 테스트에서 확인) 호스트 x86에서는 끈다
    bool weight_repack   = true;
    bool flash_attn      = true;
    bool kv_q8           = false;  // MLA 캐시 q8_0
    // 오프로드 대상 디바이스 이름(쉼표 구분, 예: "HTP0" / "GPUOpenCL"). 비어 있으면 CPU 전용
    std::string devices;
    // 오프로드 중에도 CPU 메모리(mmap)에 둘 텐서 이름 정규식. 예: "\\.ffn_.*_exps\\." = MoE routed expert
    // → RAM 보다 큰 MoE 모델에서 expert 는 저장장치에서 읽고 나머지(어텐션·공유 expert·출력)는 GPU/NPU 로
    std::string cpu_tensors;
    int  n_gpu_layers    = 0;
    int  max_checkpoints = 8;
};

struct SamplerParams {
    float    temperature    = 1.0f;
    float    top_p          = 0.95f;
    int      top_k          = 20;
    float    min_p          = 0.0f;
    float    repeat_penalty = 1.0f;
    float    presence_penalty = 0.0f;  // Qwen3.6 권장 1.5 (반복 루프 억제)
    int      repeat_last_n  = 64;
    uint32_t seed           = 0xFFFFFFFF;  // LLAMA_DEFAULT_SEED
    // GBNF 문법(비우면 끔). trigger 가 있으면 lazy: 생성 텍스트가 그 정규식(첫 캡처 그룹부터 문법 적용)에 걸린 뒤에만 제약
    std::string grammar;
    std::string grammar_trigger;
};

// 프롬프트 조각: 텍스트(토큰화 대상) 또는 이미 알고 있는 토큰열(이전에 생성한 assistant 출력)
struct Segment {
    std::string       text;
    std::vector<token> tokens;
    bool              is_tokens = false;
    // 이 조각 끝을 체크포인트 후보 지점(턴 경계)으로 표시
    bool              boundary_after = false;
};

struct SyncResult {
    bool ok             = false;
    int  n_prompt       = 0;  // 프롬프트 전체 토큰 수
    int  n_reused       = 0;  // 캐시에서 재사용한 토큰 수
    int  n_prefilled    = 0;  // 이번에 계산한 토큰 수
    bool restored_ckpt  = false;
    bool full_reset     = false;
    double prefill_ms   = 0;
    std::string error;
};

enum class StopReason { Eog, MaxTokens, Cancelled, ContextFull, Error };

struct GenerateResult {
    StopReason         reason = StopReason::Error;
    std::vector<token> tokens;  // 생성 토큰(EOG 제외), 컨텍스트에 실제 반영된 것만
    std::string        text;    // tokens 의 디토큰화 결과
    double             decode_ms = 0;
    double             ttft_ms   = 0;  // sync 이후 첫 토큰까지 (샘플링 포함)
    std::string        error;
};

struct BenchResult {
    int    n_prompt = 0, n_gen = 0;
    double pp_tps = 0, tg_tps = 0;
    double pp_ms = 0, tg_ms = 0;
};

struct Checkpoint {
    int                  n_tokens = 0;  // 이 상태가 반영한 토큰 수(= 다음 위치)
    std::vector<uint8_t> data;
};

// on_piece(text, token): false 반환 시 중단
using PieceCallback    = std::function<bool(const std::string &, token)>;
using ProgressCallback = std::function<void(int done, int total)>;

class Engine {
public:
    Engine();
    ~Engine();
    Engine(const Engine &)             = delete;
    Engine & operator=(const Engine &) = delete;

    // 프로세스당 1회. backend_dir: 동적 백엔드(.so) 경로.
    // 지정 시 CPU 변형 중 최고 점수 하나만 로드한다(GPU/NPU 는 load_backend 로 필요할 때만 —
    // Hexagon 은 등록 시점에 FastRPC 세션을 열기 때문). 빈 문자열이면 ggml 기본 탐색으로 전부 로드
    static void global_init(const std::string & backend_dir);
    // 추가 백엔드 지연 로드 (name: "opencl" | "hexagon"). 이미 로드됐거나 성공 시 true
    static bool load_backend(const std::string & name);
    static std::string system_info();
    static std::vector<std::string> list_devices();

    bool load(const EngineParams & p, std::string & err, const std::function<bool(float)> & progress = {});
    void unload();
    bool loaded() const { return ctx_ != nullptr; }

    // 스레드 수/코어 배치 변경(자동 튜닝·열 관리용). 스레드풀 재생성 포함, 재로드 불필요
    void set_threads(int n_threads, int n_threads_batch, const std::string & cpumask);

    std::vector<token> tokenize(const std::string & text, bool parse_special = true) const;
    std::string        detokenize(const std::vector<token> & toks) const;
    // boundaries(옵션): boundary_after 조각의 끝 위치들
    std::vector<token> build_tokens(const std::vector<Segment> & segs, std::vector<int> * boundaries = nullptr) const;

    // 컨텍스트를 prompt 상태로 맞춘다(LCP 재사용 + 체크포인트 복원 + 차이분 prefill).
    // 마지막 토큰의 logits가 준비된 상태로 반환한다.
    // boundaries: 턴 경계 위치. prefill 도중 해당 지점마다 체크포인트를 남겨, 이후 그 턴 이후를
    // 편집해도 처음부터 다시 계산하지 않게 한다.
    SyncResult sync(const std::vector<token> & prompt, const std::vector<int> & boundaries = {},
                    const ProgressCallback & progress = {});

    GenerateResult generate(int max_tokens, const SamplerParams & sp, const PieceCallback & on_piece);

    // 현재 위치 스냅샷 저장(재귀 상태만). 이미 같은 위치가 있으면 무시
    bool checkpoint_now();
    void clear_checkpoints() { ckpts_.clear(); }
    size_t checkpoint_count() const { return ckpts_.size(); }
    size_t checkpoint_bytes() const;

    void reset();  // 컨텍스트/체크포인트 모두 비움
    void cancel() { cancel_.store(true); }

    BenchResult bench(int n_prompt, int n_gen, int reps);

    // 정확성 검사용: 주어진 토큰열을 처음부터 계산한 마지막 logits (vocab 크기)
    // n_single > 0: 마지막 n_single 토큰은 1개씩 디코드(생성 경로 검사), 나머지는 배치 prefill
    std::vector<float> eval_logits(const std::vector<token> & toks, int n_single = 0);

    const std::vector<token> & context_tokens() const { return tokens_; }
    int    n_ctx() const;
    int    n_vocab() const;
    token  eos() const;
    bool   is_eog(token t) const;
    std::string model_desc() const;
    uint64_t    model_size() const;
    uint64_t    model_n_params() const;
    const EngineParams & params() const { return params_; }

private:
    bool decode_range(const std::vector<token> & toks, int from, int to, bool want_last_logits,
                      const ProgressCallback & progress, int progress_base, int progress_total);
    bool restore_for(int n_common, int & n_past, bool & restored, bool & reset);
    void prune_checkpoints_after(int n_past);
    void make_threadpools();
    void free_threadpools();
    std::string piece(token t) const;

    EngineParams         params_;
    llama_model *        model_ = nullptr;
    llama_context *      ctx_   = nullptr;
    const llama_vocab *  vocab_ = nullptr;
    ggml_threadpool *    tp_       = nullptr;
    ggml_threadpool *    tp_batch_ = nullptr;
    std::vector<token>   tokens_;
    std::vector<Checkpoint> ckpts_;
    std::atomic<bool>    cancel_{false};
    bool                 logits_ready_ = false;
};

// 최장 공통 접두부 길이
int common_prefix(const std::vector<token> & a, const std::vector<token> & b);

// 불완전한 UTF-8 꼬리 바이트 수(다음 조각과 합쳐야 하는 길이)
size_t utf8_incomplete_tail(const std::string & s);

}  // namespace ling
