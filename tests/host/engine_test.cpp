// ling_engine 통합 테스트 (실제 Ling-3.0-tiny GGUF 필요)
// 검증 항목
//  1. 로드/토큰화 왕복
//  2. 재생성 시나리오: 같은 프롬프트 재동기화 → 체크포인트 복원 + 1토큰만 prefill, greedy 결과 동일
//  3. 다음 턴 append: 이전 프롬프트+생성 토큰 전부 재사용, 차이분만 prefill
//  4. 편집 시나리오: 중간 지점으로 되감기 → 체크포인트 복원
//  5. 증분 결과 vs 처음부터 prefill 결과 비교(greedy 토큰 일치율)
//  6. 취소 동작, 간단 벤치
#include "ling_engine.h"

#include <cstdio>
#include <cstdlib>
#include <string>

using namespace ling;

static int g_fail = 0;
#define CHECK(cond, msg)                                              \
    do {                                                              \
        if (!(cond)) {                                                \
            std::printf("  [FAIL] %s  (%s:%d)\n", msg, __FILE__, __LINE__); \
            ++g_fail;                                                 \
        } else {                                                      \
            std::printf("  [ OK ] %s\n", msg);                        \
        }                                                             \
    } while (0)

static std::string prompt_text(const std::string & user, bool thinking) {
    return std::string("<role>SYSTEM</role>You are a helpful assistant.\ndetailed thinking ") +
           (thinking ? "on" : "off") + "<|role_end|><role>HUMAN</role>" + user +
           "<|role_end|><role>ASSISTANT</role>" + (thinking ? "\n<think>" : "\n<think></think>");
}

static void print_sync(const char * tag, const SyncResult & r) {
    std::printf("  %s: ok=%d prompt=%d reused=%d prefilled=%d restored=%d reset=%d %.1fms %s\n", tag, r.ok, r.n_prompt,
                r.n_reused, r.n_prefilled, r.restored_ckpt, r.full_reset, r.prefill_ms, r.error.c_str());
}

static int match_len(const std::vector<token> & a, const std::vector<token> & b) {
    return common_prefix(a, b);
}

int main(int argc, char ** argv) {
    if (argc < 2) {
        std::fprintf(stderr, "usage: %s model.gguf [threads]\n", argv[0]);
        return 2;
    }
    const int threads = argc > 2 ? std::atoi(argv[2]) : 4;

    // utf8 helper 단위 검사
    std::printf("[utf8]\n");
    CHECK(utf8_incomplete_tail("abc") == 0, "ascii complete");
    CHECK(utf8_incomplete_tail(std::string("\xEA\xB0", 2)) == 2, "partial 3-byte (가)");
    CHECK(utf8_incomplete_tail("\xEA\xB0\x80") == 0, "complete 3-byte (가)");
    CHECK(utf8_incomplete_tail(std::string("a\xF0\x9F\x98", 4)) == 3, "partial 4-byte emoji");

    Engine::global_init("");
    Engine e;
    EngineParams p;
    p.model_path      = argv[1];
    p.n_ctx           = 4096;
    p.n_threads       = threads;
    p.n_threads_batch = threads;
#if defined(__x86_64__)
    p.weight_repack = false;  // x86 AMX extra buffer는 MLA attn_k_b(3D)에서 미지원 → 스케줄러 assert
#endif
    std::string err;
    std::printf("[load]\n");
    const bool loaded = e.load(p, err);
    CHECK(loaded, ("load " + err).c_str());
    if (!loaded) {
        return 1;
    }
    std::printf("  desc=%s size=%.2fGB params=%.2fB vocab=%d eos=%d\n", e.model_desc().c_str(), e.model_size() / 1e9,
                e.model_n_params() / 1e9, e.n_vocab(), e.eos());

    std::printf("[tokenize]\n");
    const std::string hello = "안녕하세요, Ling! 17 × 23 = ?";
    CHECK(e.detokenize(e.tokenize(hello)) == hello, "round-trip korean text");
    const auto special = e.tokenize("<role>HUMAN</role>hi<|role_end|>");
    CHECK(!special.empty() && e.is_eog(special.back()), "<|role_end|> is a single EOG special token");

    SamplerParams greedy;
    greedy.temperature = 0.0f;

    // ---- 2. 재생성 시나리오 ----
    std::printf("[regenerate]\n");
    const auto A  = e.tokenize(prompt_text("What is the capital of France? Answer in one word.", false));
    auto       s1 = e.sync(A);
    print_sync("sync A", s1);
    CHECK(s1.ok && s1.n_reused == 0 && s1.n_prefilled == (int) A.size(), "first sync prefills everything");
    CHECK(e.checkpoint_count() == 1, "checkpoint created at |A|-1");
    std::printf("  checkpoint bytes=%.1f MiB\n", e.checkpoint_bytes() / 1048576.0);
    auto g1 = e.generate(24, greedy, {});
    std::printf("  G1 (%d tok, %.1f tok/s): %s\n", (int) g1.tokens.size(), g1.tokens.size() * 1000.0 / g1.decode_ms,
                g1.text.c_str());
    CHECK(!g1.tokens.empty(), "generated tokens");

    auto s2 = e.sync(A);
    print_sync("re-sync A", s2);
    CHECK(s2.ok && s2.restored_ckpt && s2.n_prefilled == 1, "regenerate restores checkpoint and prefills 1 token");
    auto g1b = e.generate(24, greedy, {});
    CHECK(g1b.tokens == g1.tokens, "regenerated greedy output identical");

    // ---- 3. 다음 턴 append ----
    std::printf("[append]\n");
    const auto ctx_after = e.context_tokens();  // A + G1
    std::vector<Segment> segs;
    segs.push_back({ prompt_text("What is the capital of France? Answer in one word.", false), {}, false, false });
    segs.push_back({ "", g1b.tokens, true, true });  // assistant 응답 끝 = 턴 경계
    segs.push_back({ "<|role_end|><role>HUMAN</role>And of Germany?<|role_end|><role>ASSISTANT</role>\n<think></think>",
                     {}, false, false });
    std::vector<int> bnd;
    const auto B  = e.build_tokens(segs, &bnd);
    CHECK(bnd.size() == 1 && bnd[0] == (int) ctx_after.size(), "boundary at end of assistant turn");
    auto       s3 = e.sync(B, bnd);
    print_sync("sync B", s3);
    CHECK(s3.ok && s3.n_reused == (int) ctx_after.size() && !s3.restored_ckpt && !s3.full_reset,
          "append reuses all previous tokens");
    auto g2 = e.generate(24, greedy, {});
    std::printf("  G2: %s\n", g2.text.c_str());

    // ---- 5. 처음부터 계산한 결과와 비교 ----
    e.reset();
    auto s4 = e.sync(B, bnd);
    std::printf("  checkpoints after fresh B: %zu\n", e.checkpoint_count());
    print_sync("fresh B", s4);
    auto g2f = e.generate(24, greedy, {});
    const int m = match_len(g2.tokens, g2f.tokens);
    std::printf("  incremental vs fresh greedy match: %d/%d\n", m, (int) g2f.tokens.size());
    CHECK(m >= std::min<int>(8, (int) g2f.tokens.size()), "incremental prefill ~= fresh prefill (>=8 leading tokens)");

    // ---- 4. 편집 시나리오: 두 번째 질문을 바꾼다 ----
    std::printf("[edit]\n");
    std::vector<Segment> segs2 = segs;
    segs2.back().text = "<|role_end|><role>HUMAN</role>And of Italy?<|role_end|><role>ASSISTANT</role>\n<think></think>";
    std::vector<int> bnd2;
    const auto C  = e.build_tokens(segs2, &bnd2);
    auto       s5 = e.sync(C, bnd2);
    print_sync("sync C", s5);
    CHECK(s5.ok && s5.restored_ckpt && !s5.full_reset && s5.n_reused == bnd2[0],
          "edit restores the turn-boundary checkpoint, no full reset");
    auto g3 = e.generate(24, greedy, {});
    std::printf("  G3: %s\n", g3.text.c_str());

    // ---- thinking 모드 샘플 ----
    std::printf("[thinking sample]\n");
    const auto T  = e.tokenize(prompt_text("Calculate 17 * 23. Output only the number at the end.", true));
    auto       s6 = e.sync(T);
    print_sync("sync T", s6);
    SamplerParams sp;  // 권장값 temp 1.0 / top_p 0.95 / top_k 20
    sp.seed = 42;
    int n_cb = 0;
    auto gt = e.generate(400, sp, [&](const std::string &, token) { ++n_cb; return true; });
    std::printf("  reason=%d tokens=%d callbacks=%d %.1f tok/s\n---\n%s\n---\n", (int) gt.reason, (int) gt.tokens.size(),
                n_cb, gt.tokens.size() * 1000.0 / gt.decode_ms, gt.text.c_str());
    CHECK(gt.text.find("</think>") != std::string::npos || gt.reason == StopReason::MaxTokens, "thinking block closes");

    // ---- 6. 취소 ----
    std::printf("[cancel]\n");
    e.sync(T);
    int  seen = 0;
    auto gc   = e.generate(100, greedy, [&](const std::string &, token) { return ++seen < 5; });
    CHECK(gc.reason == StopReason::Cancelled && (int) gc.tokens.size() == 5, "callback false stops after 5 tokens");
    CHECK((int) e.context_tokens().size() == (int) T.size() + 5, "context includes exactly emitted tokens");

    // ---- 벤치 ----
    std::printf("[bench]\n");
    auto br = e.bench(128, 32, 1);
    std::printf("  pp128 %.1f tok/s, tg32 %.1f tok/s (threads=%d)\n", br.pp_tps, br.tg_tps, threads);
    CHECK(br.pp_tps > 0 && br.tg_tps > 0, "bench runs");

    std::printf("\n%s (%d failures)\n", g_fail ? "FAILED" : "ALL PASSED", g_fail);
    return g_fail ? 1 : 0;
}
