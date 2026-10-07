#include "ling_engine.h"

#include "ggml-backend.h"
#include "ggml-cpu.h"
#include "llama.h"

#include <algorithm>
#include <dirent.h>
#include <dlfcn.h>
#include <chrono>
#include <cstring>
#include <sstream>

namespace ling {

namespace {

using clk = std::chrono::steady_clock;

double ms_since(clk::time_point t0) {
    return std::chrono::duration<double, std::milli>(clk::now() - t0).count();
}

// 체크포인트를 새로 만들 최소 간격(토큰). 너무 촘촘하면 메모리만 소모
constexpr int kMinCheckpointGap = 32;

bool abort_cb(void * data) {
    return static_cast<std::atomic<bool> *>(data)->load();
}

std::vector<std::string> split_csv(const std::string & s) {
    std::vector<std::string> out;
    std::stringstream ss(s);
    std::string item;
    while (std::getline(ss, item, ',')) {
        item.erase(0, item.find_first_not_of(" \t"));
        item.erase(item.find_last_not_of(" \t") + 1);
        if (!item.empty()) {
            out.push_back(item);
        }
    }
    return out;
}

}  // namespace

int common_prefix(const std::vector<token> & a, const std::vector<token> & b) {
    const size_t n = std::min(a.size(), b.size());
    size_t i = 0;
    while (i < n && a[i] == b[i]) {
        ++i;
    }
    return (int) i;
}

size_t utf8_incomplete_tail(const std::string & s) {
    // 끝에서 최대 3바이트를 보고, 시작 바이트가 요구하는 길이보다 짧으면 그만큼 보류
    const size_t n = s.size();
    for (size_t back = 1; back <= 4 && back <= n; ++back) {
        const auto c = (unsigned char) s[n - back];
        if ((c & 0xC0) == 0x80) {
            continue;  // continuation byte
        }
        size_t need = 1;
        if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        return need > back ? back : 0;
    }
    return 0;
}

Engine::Engine() = default;

Engine::~Engine() {
    unload();
}

namespace {
std::string g_backend_dir;
}

void Engine::global_init(const std::string & backend_dir) {
    static bool done = false;
    if (done) {
        return;
    }
    done = true;
    g_backend_dir = backend_dir;
    if (backend_dir.empty()) {
        ggml_backend_load_all();
    } else {
        // CPU 변형(libggml-cpu-*.so) 중 이 CPU 에서 지원되는 최고 점수 선택 (ggml load_best 와 같은 규칙)
        std::string best;
        int         best_score = 0;
        if (DIR * d = opendir(backend_dir.c_str())) {
            while (dirent * e = readdir(d)) {
                const std::string n = e->d_name;
                if (n.rfind("libggml-cpu", 0) != 0 || n.size() < 3 || n.substr(n.size() - 3) != ".so") {
                    continue;
                }
                const std::string path = backend_dir + "/" + n;
                void * h = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
                if (!h) {
                    continue;
                }
                auto score_fn = (int (*)()) dlsym(h, "ggml_backend_score");
                const int score = score_fn ? score_fn() : 0;
                dlclose(h);
                if (score > best_score) {
                    best_score = score;
                    best       = path;
                }
            }
            closedir(d);
        }
        if (best.empty() || !ggml_backend_load(best.c_str())) {
            ggml_backend_load_all_from_path(backend_dir.c_str());  // 폴백
        }
    }
    llama_backend_init();
}

bool Engine::load_backend(const std::string & name) {
    for (size_t i = 0; i < ggml_backend_reg_count(); ++i) {
        std::string rn = ggml_backend_reg_name(ggml_backend_reg_get(i));
        std::transform(rn.begin(), rn.end(), rn.begin(), ::tolower);
        if (rn.find(name) != std::string::npos || (name == "hexagon" && rn.find("htp") != std::string::npos)) {
            return true;
        }
    }
    if (g_backend_dir.empty()) {
        return false;
    }
    const std::string path = g_backend_dir + "/libggml-" + name + ".so";
    return ggml_backend_load(path.c_str()) != nullptr;
}

std::string Engine::system_info() {
    return llama_print_system_info();
}

std::vector<std::string> Engine::list_devices() {
    std::vector<std::string> out;
    for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
        auto * dev = ggml_backend_dev_get(i);
        size_t free = 0, total = 0;
        ggml_backend_dev_memory(dev, &free, &total);
        std::string type;
        switch (ggml_backend_dev_type(dev)) {
            case GGML_BACKEND_DEVICE_TYPE_CPU:   type = "CPU"; break;
            case GGML_BACKEND_DEVICE_TYPE_GPU:   type = "GPU"; break;
            case GGML_BACKEND_DEVICE_TYPE_IGPU:  type = "IGPU"; break;
            case GGML_BACKEND_DEVICE_TYPE_ACCEL: type = "ACCEL"; break;
            default:                             type = "OTHER"; break;
        }
        out.push_back(std::string(ggml_backend_dev_name(dev)) + "|" + type + "|" +
                      ggml_backend_dev_description(dev) + "|" + std::to_string(total / (1024 * 1024)));
    }
    return out;
}

bool Engine::load(const EngineParams & p, std::string & err, const std::function<bool(float)> & progress) {
    unload();
    params_ = p;

    auto mp = llama_model_default_params();
    if (p.use_mmap) {
        mp.load_mode = p.use_mlock ? LLAMA_LOAD_MODE_MMAP_MLOCK : LLAMA_LOAD_MODE_MMAP;
    } else {
        mp.load_mode = p.use_mlock ? LLAMA_LOAD_MODE_MLOCK : LLAMA_LOAD_MODE_NONE;
    }

    mp.use_extra_bufts = p.weight_repack;

    // 디바이스 목록: 비어 있으면 CPU 전용(빈 NULL-종료 목록), 아니면 이름으로 조회
    std::vector<ggml_backend_dev_t> devs;
    for (const auto & name : split_csv(p.devices)) {
        auto * dev = ggml_backend_dev_by_name(name.c_str());
        if (!dev) {
            err = "device not found: " + name;
            return false;
        }
        devs.push_back(dev);
    }
    const bool offload = !devs.empty();
    devs.push_back(nullptr);
    mp.devices      = offload ? devs.data() : nullptr;
    mp.n_gpu_layers = offload ? (p.n_gpu_layers > 0 ? p.n_gpu_layers : 999) : 0;

    struct ProgressCtx {
        const std::function<bool(float)> * fn;
    } pctx{ &progress };
    if (progress) {
        mp.progress_callback = [](float v, void * ud) -> bool {
            return (*static_cast<ProgressCtx *>(ud)->fn)(v);
        };
        mp.progress_callback_user_data = &pctx;
    }

    model_ = llama_model_load_from_file(p.model_path.c_str(), mp);
    if (!model_) {
        err = "failed to load model: " + p.model_path;
        return false;
    }
    vocab_ = llama_model_get_vocab(model_);

    auto cp            = llama_context_default_params();
    cp.n_ctx           = (uint32_t) p.n_ctx;
    cp.n_batch         = (uint32_t) p.n_batch;
    cp.n_ubatch        = (uint32_t) std::min(p.n_ubatch, p.n_batch);
    cp.n_seq_max       = 1;
    cp.n_threads       = p.n_threads;
    cp.n_threads_batch = p.n_threads_batch;
    cp.flash_attn_type = p.flash_attn ? LLAMA_FLASH_ATTN_TYPE_AUTO : LLAMA_FLASH_ATTN_TYPE_DISABLED;
    if (p.kv_q8) {
        cp.type_k = GGML_TYPE_Q8_0;
        cp.type_v = GGML_TYPE_Q8_0;
    }
    cp.no_perf             = false;
    cp.abort_callback      = abort_cb;
    cp.abort_callback_data = &cancel_;

    ctx_ = llama_init_from_model(model_, cp);
    if (!ctx_) {
        err = "failed to create context (n_ctx=" + std::to_string(p.n_ctx) + ")";
        llama_model_free(model_);
        model_ = nullptr;
        return false;
    }

    make_threadpools();
    tokens_.clear();
    ckpts_.clear();
    logits_ready_ = false;

    // 워밍업: mmap 된(재배열되지 않은) 텐서의 첫 페이지 폴트와 그래프 할당을 로드 단계에서 끝낸다.
    // 호스트 실측: 콜드 상태 첫 prefill 31토큰 22s → 워밍업 후 0.5s
    {
        std::vector<token> warm = { eos(), eos() };
        decode_range(warm, 0, (int) warm.size(), true, {}, 0, (int) warm.size());
        llama_synchronize(ctx_);
        reset();
        llama_perf_context_reset(ctx_);
    }
    return true;
}

void Engine::make_threadpools() {
    free_threadpools();
    // 지속 스레드풀을 항상 만든다: 미부착 시 ggml 이 매 compute 마다 임시 스레드를 생성하므로
    // 토큰당 수십~수백 µs 오버헤드가 생긴다(디코드는 토큰마다 compute 1회)
    const auto cpus = split_csv(params_.cpumask);
    auto * reg = ggml_backend_reg_by_name("CPU");
    if (!reg) {
        return;
    }
    auto * new_fn = (decltype(ggml_threadpool_new) *) ggml_backend_reg_get_proc_address(reg, "ggml_threadpool_new");
    if (!new_fn) {
        return;
    }
    auto make = [&](int n) {
        auto tpp = ggml_threadpool_params_default(n);
        tpp.poll       = (uint32_t) std::clamp(params_.poll, 0, 100);
        tpp.strict_cpu = params_.strict_cpu && !cpus.empty();
        for (const auto & c : cpus) {
            const int id = std::atoi(c.c_str());
            if (id >= 0 && id < GGML_MAX_N_THREADS) {
                tpp.cpumask[id] = true;
            }
        }
        return new_fn(&tpp);
    };
    tp_       = make(params_.n_threads);
    tp_batch_ = params_.n_threads_batch == params_.n_threads ? nullptr : make(params_.n_threads_batch);
    llama_attach_threadpool(ctx_, tp_, tp_batch_ ? tp_batch_ : tp_);
}

void Engine::free_threadpools() {
    if (!tp_ && !tp_batch_) {
        return;
    }
    if (ctx_) {
        llama_detach_threadpool(ctx_);
    }
    auto * reg     = ggml_backend_reg_by_name("CPU");
    auto * free_fn = reg ? (decltype(ggml_threadpool_free) *) ggml_backend_reg_get_proc_address(reg, "ggml_threadpool_free")
                         : nullptr;
    if (free_fn) {
        if (tp_) free_fn(tp_);
        if (tp_batch_) free_fn(tp_batch_);
    }
    tp_ = tp_batch_ = nullptr;
}

void Engine::set_threads(int n_threads, int n_threads_batch, const std::string & cpumask) {
    if (!ctx_) {
        return;
    }
    params_.n_threads       = n_threads;
    params_.n_threads_batch = n_threads_batch;
    params_.cpumask         = cpumask;
    llama_set_n_threads(ctx_, n_threads, n_threads_batch);
    make_threadpools();
}

void Engine::unload() {
    free_threadpools();
    if (ctx_) {
        llama_free(ctx_);
        ctx_ = nullptr;
    }
    if (model_) {
        llama_model_free(model_);
        model_ = nullptr;
    }
    vocab_ = nullptr;
    tokens_.clear();
    ckpts_.clear();
    logits_ready_ = false;
}

std::vector<token> Engine::tokenize(const std::string & text, bool parse_special) const {
    if (!vocab_ || text.empty()) {
        return {};
    }
    std::vector<token> out(text.size() + 8);
    int n = llama_tokenize(vocab_, text.data(), (int32_t) text.size(), out.data(), (int32_t) out.size(), false,
                           parse_special);
    if (n < 0) {
        out.resize(-n);
        n = llama_tokenize(vocab_, text.data(), (int32_t) text.size(), out.data(), (int32_t) out.size(), false,
                           parse_special);
    }
    out.resize(std::max(n, 0));
    return out;
}

// special=true: 제어 토큰도 문자열로 낸다. LFM2.5 의 <|tool_call_start|>/<|tool_call_end|> 같은 툴 호출 경계가
// 제어 토큰이라, 끄면 앱 파서가 호출을 찾지 못한다. EOG 는 생성 루프에서 piece 변환 전에 걸러진다.
std::string Engine::piece(token t) const {
    char buf[256];
    int  n = llama_token_to_piece(vocab_, t, buf, sizeof(buf), 0, true);
    if (n < 0) {
        std::string big(-n, '\0');
        llama_token_to_piece(vocab_, t, big.data(), (int32_t) big.size(), 0, true);
        return big;
    }
    return std::string(buf, n);
}

std::string Engine::detokenize(const std::vector<token> & toks) const {
    std::string out;
    for (auto t : toks) {
        out += piece(t);
    }
    return out;
}

std::vector<token> Engine::build_tokens(const std::vector<Segment> & segs, std::vector<int> * boundaries) const {
    std::vector<token> out;
    for (const auto & s : segs) {
        if (s.is_tokens) {
            out.insert(out.end(), s.tokens.begin(), s.tokens.end());
        } else {
            auto t = tokenize(s.text, true);
            out.insert(out.end(), t.begin(), t.end());
        }
        if (s.boundary_after && boundaries && !out.empty()) {
            boundaries->push_back((int) out.size());
        }
    }
    return out;
}

bool Engine::decode_range(const std::vector<token> & toks, int from, int to, bool want_last_logits,
                          const ProgressCallback & progress, int progress_base, int progress_total) {
    const int n_batch = std::max(1, params_.n_batch);
    llama_batch batch = llama_batch_init(n_batch, 0, 1);
    bool        ok    = true;
    for (int i = from; i < to; i += n_batch) {
        const int n = std::min(n_batch, to - i);
        batch.n_tokens = n;
        for (int j = 0; j < n; ++j) {
            batch.token[j]     = toks[i + j];
            batch.pos[j]       = i + j;
            batch.n_seq_id[j]  = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j]    = want_last_logits && (i + j == to - 1);
        }
        const int rc = llama_decode(ctx_, batch);
        if (rc != 0) {
            // 중단/오류: 메모리에 실제 반영된 위치까지 tokens_를 맞춘다
            const llama_pos pmax = llama_memory_seq_pos_max(llama_get_memory(ctx_), 0);
            tokens_.assign(toks.begin(), toks.begin() + std::max(0, (int) pmax + 1));
            ok = false;
            break;
        }
        tokens_.insert(tokens_.end(), toks.begin() + i, toks.begin() + i + n);
        if (progress) {
            progress(progress_base + (i + n - from), progress_total);
        }
    }
    llama_batch_free(batch);
    return ok;
}

void Engine::prune_checkpoints_after(int n_past) {
    ckpts_.erase(std::remove_if(ckpts_.begin(), ckpts_.end(),
                                [n_past](const Checkpoint & c) { return c.n_tokens > n_past; }),
                 ckpts_.end());
}

bool Engine::restore_for(int n_common, int & n_past, bool & restored, bool & reset) {
    auto * mem = llama_get_memory(ctx_);
    // 1) 재귀 메모리가 허용하는 범위면 바로 잘라낸다
    if (llama_memory_seq_rm(mem, 0, n_common, -1)) {
        n_past = n_common;
        return true;
    }
    // 2) n_common 이하에서 가장 가까운 체크포인트 복원
    const Checkpoint * best = nullptr;
    for (const auto & c : ckpts_) {
        if (c.n_tokens <= n_common && c.n_tokens <= (int) tokens_.size() && (!best || c.n_tokens > best->n_tokens)) {
            best = &c;
        }
    }
    if (best) {
        const size_t n = llama_state_seq_set_data_ext(ctx_, best->data.data(), best->data.size(), 0,
                                                      LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY);
        if (n > 0 && llama_memory_seq_rm(mem, 0, best->n_tokens, -1)) {
            n_past   = best->n_tokens;
            restored = true;
            return true;
        }
    }
    // 3) 전체 초기화
    llama_memory_clear(mem, true);
    n_past = 0;
    reset  = true;
    return true;
}

SyncResult Engine::sync(const std::vector<token> & prompt, const std::vector<int> & boundaries,
                        const ProgressCallback & progress) {
    SyncResult r;
    r.n_prompt = (int) prompt.size();
    if (!ctx_) {
        r.error = "model not loaded";
        return r;
    }
    if (prompt.empty()) {
        r.error = "empty prompt";
        return r;
    }
    if ((int) prompt.size() >= n_ctx()) {
        r.error = "prompt exceeds context (" + std::to_string(prompt.size()) + " >= " + std::to_string(n_ctx()) + ")";
        return r;
    }
    cancel_.store(false);
    const auto t0 = clk::now();

    // 마지막 토큰은 logits를 얻기 위해 반드시 다시 평가한다
    int n_common = std::min(common_prefix(tokens_, prompt), (int) prompt.size() - 1);
    int n_past   = (int) tokens_.size();
    if (n_common < n_past) {
        restore_for(n_common, n_past, r.restored_ckpt, r.full_reset);
        tokens_.resize(n_past);
    }
    prune_checkpoints_after(n_past);
    r.n_reused = n_past;

    const int last  = (int) prompt.size() - 1;
    const int total = last + 1 - n_past;
    logits_ready_   = false;

    // 체크포인트 지점: 턴 경계 + 마지막 토큰 직전(재생성용)
    std::vector<int> stops;
    for (int b : boundaries) {
        if (b > n_past && b < last) {
            stops.push_back(b);
        }
    }
    stops.push_back(last);
    std::sort(stops.begin(), stops.end());
    stops.erase(std::unique(stops.begin(), stops.end()), stops.end());

    int pos = n_past;
    for (int stop : stops) {
        if (pos < stop) {
            if (!decode_range(prompt, pos, stop, false, progress, pos - n_past, total)) {
                r.error = cancel_.load() ? "cancelled" : "decode failed during prefill";
                return r;
            }
            pos = stop;
        }
        // 재생성 지점(last)은 항상, 턴 경계는 직전 체크포인트와 충분히 떨어져 있을 때만 저장
        const int prev = ckpts_.empty() ? 0 : ckpts_.back().n_tokens;
        if (pos > 0 && (pos == last || pos - prev >= kMinCheckpointGap)) {
            checkpoint_now();
        }
    }
    if (!decode_range(prompt, last, last + 1, true, progress, total - 1, total)) {
        r.error = cancel_.load() ? "cancelled" : "decode failed";
        return r;
    }
    logits_ready_ = true;
    r.n_prefilled = total;
    r.prefill_ms  = ms_since(t0);
    r.ok          = true;
    return r;
}

bool Engine::checkpoint_now() {
    if (!ctx_ || tokens_.empty()) {
        return false;
    }
    const int n = (int) tokens_.size();
    for (const auto & c : ckpts_) {
        if (c.n_tokens == n) {
            return true;
        }
    }
    const size_t sz = llama_state_seq_get_size_ext(ctx_, 0, LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY);
    if (sz == 0) {
        return false;
    }
    Checkpoint c;
    c.n_tokens = n;
    c.data.resize(sz);
    if (llama_state_seq_get_data_ext(ctx_, c.data.data(), sz, 0, LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY) != sz) {
        return false;
    }
    ckpts_.push_back(std::move(c));
    std::sort(ckpts_.begin(), ckpts_.end(), [](const auto & a, const auto & b) { return a.n_tokens < b.n_tokens; });
    // 오래된(앞쪽) 것부터 버리되, 가장 앞(시스템 프롬프트 직후)은 남긴다
    while ((int) ckpts_.size() > std::max(1, params_.max_checkpoints)) {
        ckpts_.erase(ckpts_.size() > 2 ? ckpts_.begin() + 1 : ckpts_.begin());
    }
    return true;
}

size_t Engine::checkpoint_bytes() const {
    size_t s = 0;
    for (const auto & c : ckpts_) {
        s += c.data.size();
    }
    return s;
}

void Engine::reset() {
    if (ctx_) {
        llama_memory_clear(llama_get_memory(ctx_), true);
    }
    tokens_.clear();
    ckpts_.clear();
    logits_ready_ = false;
}

GenerateResult Engine::generate(int max_tokens, const SamplerParams & sp, const PieceCallback & on_piece) {
    GenerateResult g;
    if (!ctx_ || !logits_ready_) {
        g.error = "sync() must be called before generate()";
        return g;
    }
    cancel_.store(false);

    auto * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    // 157K 어휘 → top_k로 먼저 줄여 이후 penalty/정렬 비용을 최소화
    if (sp.top_k > 0 && sp.temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(sp.top_k));
    }
    if (sp.repeat_penalty != 1.0f || sp.presence_penalty != 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(n_vocab(), sp.repeat_last_n, sp.repeat_penalty, 0.0f,
                                                                   sp.presence_penalty));
    }
    if (sp.temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        if (sp.top_p < 1.0f) llama_sampler_chain_add(smpl, llama_sampler_init_top_p(sp.top_p, 1));
        if (sp.min_p > 0.0f) llama_sampler_chain_add(smpl, llama_sampler_init_min_p(sp.min_p, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(sp.temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(sp.seed));
    }

    const auto t0      = clk::now();
    std::string pending;  // UTF-8 미완성 바이트 보류
    g.reason = StopReason::MaxTokens;

    for (int i = 0; i < max_tokens; ++i) {
        if (cancel_.load()) {
            g.reason = StopReason::Cancelled;
            break;
        }
        const token t = llama_sampler_sample(smpl, ctx_, -1);
        if (i == 0) {
            g.ttft_ms = ms_since(t0);
        }
        if (llama_vocab_is_eog(vocab_, t)) {
            g.reason = StopReason::Eog;
            break;
        }
        if ((int) tokens_.size() + 1 >= n_ctx()) {
            g.reason = StopReason::ContextFull;
            break;
        }
        g.tokens.push_back(t);

        pending += piece(t);
        const size_t hold = utf8_incomplete_tail(pending);
        if (pending.size() > hold) {
            std::string out = pending.substr(0, pending.size() - hold);
            pending.erase(0, pending.size() - hold);
            if (on_piece && !on_piece(out, t)) {
                // 이미 내보낸 토큰은 컨텍스트에도 반영해 히스토리와 일치시킨 뒤 중단
                g.reason = StopReason::Cancelled;
            }
        }

        const int pos = (int) tokens_.size();
        llama_batch b = llama_batch_init(1, 0, 1);
        b.n_tokens     = 1;
        b.token[0]     = t;
        b.pos[0]       = pos;
        b.n_seq_id[0]  = 1;
        b.seq_id[0][0] = 0;
        b.logits[0]    = true;
        const int rc   = llama_decode(ctx_, b);
        llama_batch_free(b);
        if (rc != 0) {
            // 2 = abort: 토큰이 메모리에 반영되지 않았으므로 결과에서도 제외
            g.tokens.pop_back();
            logits_ready_ = false;
            if (rc == 2) {
                g.reason = StopReason::Cancelled;
            } else {
                g.reason = StopReason::Error;
                g.error  = "decode failed: " + std::to_string(rc);
            }
            break;
        }
        tokens_.push_back(t);
        if (g.reason == StopReason::Cancelled) {
            break;
        }
    }
    if (!pending.empty() && on_piece) {
        on_piece(pending, -1);
    }
    g.decode_ms = ms_since(t0);
    g.text      = detokenize(g.tokens);
    llama_sampler_free(smpl);
    return g;
}

BenchResult Engine::bench(int n_prompt, int n_gen, int reps) {
    BenchResult br;
    br.n_prompt = n_prompt;
    br.n_gen    = n_gen;
    if (!ctx_) {
        return br;
    }
    reps = std::max(1, reps);
    std::vector<token> prompt(n_prompt);
    const int n_vocab = this->n_vocab();
    for (int i = 0; i < n_prompt; ++i) {
        prompt[i] = (token) ((i * 7919 + 13) % n_vocab);  // 결정적 의사 난수 토큰
    }
    double pp = 0, tg = 0;
    for (int r = 0; r < reps; ++r) {
        reset();
        auto t0 = clk::now();
        if (n_prompt > 0) {
            decode_range(prompt, 0, n_prompt, true, {}, 0, n_prompt);
            llama_synchronize(ctx_);
        }
        pp += ms_since(t0);

        t0 = clk::now();
        llama_batch b = llama_batch_init(1, 0, 1);
        for (int i = 0; i < n_gen; ++i) {
            b.n_tokens     = 1;
            b.token[0]     = (token) ((i * 104729 + 7) % n_vocab);
            b.pos[0]       = (int) tokens_.size();
            b.n_seq_id[0]  = 1;
            b.seq_id[0][0] = 0;
            b.logits[0]    = true;
            if (llama_decode(ctx_, b) != 0) {
                break;
            }
            tokens_.push_back(b.token[0]);
        }
        llama_synchronize(ctx_);
        llama_batch_free(b);
        tg += ms_since(t0);
    }
    reset();
    br.pp_ms  = pp / reps;
    br.tg_ms  = tg / reps;
    br.pp_tps = n_prompt > 0 ? n_prompt * 1000.0 / br.pp_ms : 0;
    br.tg_tps = n_gen > 0 ? n_gen * 1000.0 / br.tg_ms : 0;
    return br;
}

std::vector<float> Engine::eval_logits(const std::vector<token> & toks, int n_single) {
    std::vector<float> out;
    if (!ctx_ || toks.empty()) {
        return out;
    }
    reset();
    const int n     = (int) toks.size();
    const int split = std::max(1, n - std::max(0, n_single));
    if (!decode_range(toks, 0, split, split == n, {}, 0, n)) {
        return out;
    }
    for (int i = split; i < n; ++i) {
        if (!decode_range(toks, i, i + 1, i == n - 1, {}, 0, n)) {
            return out;
        }
    }
    const float * lg = llama_get_logits_ith(ctx_, -1);
    out.assign(lg, lg + n_vocab());
    reset();
    return out;
}

int Engine::n_ctx() const {
    return ctx_ ? (int) llama_n_ctx(ctx_) : 0;
}

int Engine::n_vocab() const {
    return vocab_ ? llama_vocab_n_tokens(vocab_) : 0;
}

token Engine::eos() const {
    return vocab_ ? llama_vocab_eos(vocab_) : -1;
}

bool Engine::is_eog(token t) const {
    return vocab_ && llama_vocab_is_eog(vocab_, t);
}

std::string Engine::model_desc() const {
    if (!model_) {
        return {};
    }
    char buf[256];
    llama_model_desc(model_, buf, sizeof(buf));
    return buf;
}

uint64_t Engine::model_size() const {
    return model_ ? llama_model_size(model_) : 0;
}

uint64_t Engine::model_n_params() const {
    return model_ ? llama_model_n_params(model_) : 0;
}

}  // namespace ling
