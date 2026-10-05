// JNI 브리지: io.github.ssebanom.ling.engine.LingNative
// 모든 호출(cancel 제외)은 Kotlin 쪽 단일 추론 스레드에서만 들어온다고 가정한다.
#include "ling_engine.h"

#include "llama.h"

#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <cmath>
#include <string>
#include <vector>

#define TAG "LingJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

using ling::Engine;

namespace {

struct Handle {
    Engine              engine;
    std::string         last_error;
    ling::SyncResult    last_sync;
    ling::GenerateResult last_gen;
};

Handle * H(jlong h) {
    return reinterpret_cast<Handle *>(h);
}

std::string to_std(JNIEnv * env, jstring s) {
    if (!s) {
        return {};
    }
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string  out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

// Java modified-UTF-8 문제(4바이트 이모지 등)를 피하려고 byte[] → new String(bytes, UTF_8) 경로 사용
jstring to_jstring(JNIEnv * env, const std::string & s) {
    jbyteArray bytes = env->NewByteArray((jsize) s.size());
    env->SetByteArrayRegion(bytes, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
    jclass    str_cls = env->FindClass("java/lang/String");
    jmethodID ctor    = env->GetMethodID(str_cls, "<init>", "([BLjava/lang/String;)V");
    jstring   enc     = env->NewStringUTF("UTF-8");
    auto *    out     = (jstring) env->NewObject(str_cls, ctor, bytes, enc);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(enc);
    env->DeleteLocalRef(str_cls);
    return out;
}

std::string from_jstring_utf8(JNIEnv * env, jstring s) {
    if (!s) {
        return {};
    }
    jclass     str_cls  = env->FindClass("java/lang/String");
    jmethodID  getBytes = env->GetMethodID(str_cls, "getBytes", "(Ljava/lang/String;)[B");
    jstring    enc      = env->NewStringUTF("UTF-8");
    auto *     arr      = (jbyteArray) env->CallObjectMethod(s, getBytes, enc);
    const jsize n       = env->GetArrayLength(arr);
    std::string out(n, '\0');
    env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte *>(out.data()));
    env->DeleteLocalRef(arr);
    env->DeleteLocalRef(enc);
    env->DeleteLocalRef(str_cls);
    return out;
}

jintArray to_jints(JNIEnv * env, const std::vector<ling::token> & v) {
    jintArray a = env->NewIntArray((jsize) v.size());
    env->SetIntArrayRegion(a, 0, (jsize) v.size(), v.data());
    return a;
}

std::vector<ling::token> from_jints(JNIEnv * env, jintArray a) {
    std::vector<ling::token> v;
    if (!a) {
        return v;
    }
    v.resize(env->GetArrayLength(a));
    env->GetIntArrayRegion(a, 0, (jsize) v.size(), v.data());
    return v;
}

jdoubleArray to_jdoubles(JNIEnv * env, const std::vector<double> & v) {
    jdoubleArray a = env->NewDoubleArray((jsize) v.size());
    env->SetDoubleArrayRegion(a, 0, (jsize) v.size(), v.data());
    return a;
}

void log_cb(ggml_log_level level, const char * text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) {
        __android_log_print(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, "llama", "%s", text);
    }
}

}  // namespace

#define JFN(name) Java_io_github_ssebanom_ling_engine_LingNative_##name

extern "C" {

JNIEXPORT void JNICALL JFN(nativeInit)(JNIEnv * env, jclass, jstring backend_dir) {
    llama_log_set(log_cb, nullptr);
    Engine::global_init(to_std(env, backend_dir));
}

JNIEXPORT jboolean JNICALL JFN(nativeLoadBackend)(JNIEnv * env, jclass, jstring name) {
    return Engine::load_backend(to_std(env, name)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL JFN(nativeSystemInfo)(JNIEnv * env, jclass) {
    return to_jstring(env, Engine::system_info());
}

JNIEXPORT jobjectArray JNICALL JFN(nativeDevices)(JNIEnv * env, jclass) {
    const auto devs = Engine::list_devices();
    jclass       str = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray((jsize) devs.size(), str, nullptr);
    for (size_t i = 0; i < devs.size(); ++i) {
        jstring s = to_jstring(env, devs[i]);
        env->SetObjectArrayElement(out, (jsize) i, s);
        env->DeleteLocalRef(s);
    }
    return out;
}

JNIEXPORT jlong JNICALL JFN(nativeCreate)(JNIEnv *, jclass) {
    return reinterpret_cast<jlong>(new Handle());
}

JNIEXPORT void JNICALL JFN(nativeDestroy)(JNIEnv *, jclass, jlong h) {
    delete H(h);
}

// ip: [nCtx, nBatch, nUbatch, nThreads, nThreadsBatch, poll, nGpuLayers, maxCheckpoints]
// bp: [strictCpu, useMmap, useMlock, flashAttn, kvQ8, weightRepack]
JNIEXPORT jstring JNICALL JFN(nativeLoad)(JNIEnv * env, jclass, jlong h, jstring path, jintArray ip, jbooleanArray bp,
                                          jstring cpumask, jstring devices, jobject progress) {
    auto * hd = H(h);
    std::vector<jint>     iv(8);
    std::vector<jboolean> bv(6);
    env->GetIntArrayRegion(ip, 0, 8, iv.data());
    env->GetBooleanArrayRegion(bp, 0, 6, bv.data());

    ling::EngineParams p;
    p.model_path      = from_jstring_utf8(env, path);
    p.n_ctx           = iv[0];
    p.n_batch         = iv[1];
    p.n_ubatch        = iv[2];
    p.n_threads       = iv[3];
    p.n_threads_batch = iv[4];
    p.poll            = iv[5];
    p.n_gpu_layers    = iv[6];
    p.max_checkpoints = iv[7];
    p.strict_cpu      = bv[0];
    p.use_mmap        = bv[1];
    p.use_mlock       = bv[2];
    p.flash_attn      = bv[3];
    p.kv_q8           = bv[4];
    p.weight_repack   = bv[5];
    p.cpumask         = to_std(env, cpumask);
    p.devices         = to_std(env, devices);

    jmethodID on_progress = nullptr;
    if (progress) {
        jclass cls  = env->GetObjectClass(progress);
        on_progress = env->GetMethodID(cls, "onLoadProgress", "(F)Z");
        env->DeleteLocalRef(cls);
    }
    std::string err;
    const bool  ok = hd->engine.load(p, err, [&](float v) -> bool {
        if (!on_progress) {
            return true;
        }
        return env->CallBooleanMethod(progress, on_progress, v) == JNI_TRUE;
    });
    if (!ok) {
        hd->last_error = err;
        return to_jstring(env, err);
    }
    LOGI("loaded %s (%s, ctx=%d, threads=%d/%d, devices=%s)", p.model_path.c_str(), hd->engine.model_desc().c_str(),
         p.n_ctx, p.n_threads, p.n_threads_batch, p.devices.empty() ? "CPU" : p.devices.c_str());
    return nullptr;
}

JNIEXPORT void JNICALL JFN(nativeUnload)(JNIEnv *, jclass, jlong h) {
    H(h)->engine.unload();
}

JNIEXPORT void JNICALL JFN(nativeSetThreads)(JNIEnv * env, jclass, jlong h, jint n, jint nb, jstring cpumask) {
    H(h)->engine.set_threads(n, nb, to_std(env, cpumask));
}

JNIEXPORT jintArray JNICALL JFN(nativeTokenize)(JNIEnv * env, jclass, jlong h, jstring text) {
    return to_jints(env, H(h)->engine.tokenize(from_jstring_utf8(env, text), true));
}

JNIEXPORT jstring JNICALL JFN(nativeDetokenize)(JNIEnv * env, jclass, jlong h, jintArray toks) {
    return to_jstring(env, H(h)->engine.detokenize(from_jints(env, toks)));
}

// 반환: [ok, nPrompt, nReused, nPrefilled, restored, reset, prefillMs]
JNIEXPORT jdoubleArray JNICALL JFN(nativeSync)(JNIEnv * env, jclass, jlong h, jobjectArray texts, jobjectArray tokens,
                                               jbooleanArray boundaries, jobject listener) {
    auto *      hd = H(h);
    const jsize n  = env->GetArrayLength(texts);
    std::vector<jboolean> bnd(n);
    env->GetBooleanArrayRegion(boundaries, 0, n, bnd.data());

    std::vector<ling::Segment> segs(n);
    for (jsize i = 0; i < n; ++i) {
        auto * toks = (jintArray) env->GetObjectArrayElement(tokens, i);
        if (toks) {
            segs[i].tokens    = from_jints(env, toks);
            segs[i].is_tokens = true;
            env->DeleteLocalRef(toks);
        } else {
            auto * t     = (jstring) env->GetObjectArrayElement(texts, i);
            segs[i].text = from_jstring_utf8(env, t);
            env->DeleteLocalRef(t);
        }
        segs[i].boundary_after = bnd[i];
    }
    std::vector<int> boundary_pos;
    const auto       prompt = hd->engine.build_tokens(segs, &boundary_pos);

    jmethodID on_progress = nullptr;
    if (listener) {
        jclass cls  = env->GetObjectClass(listener);
        on_progress = env->GetMethodID(cls, "onPrefillProgress", "(II)V");
        env->DeleteLocalRef(cls);
    }
    auto r = hd->engine.sync(prompt, boundary_pos, [&](int done, int total) {
        if (on_progress) {
            env->CallVoidMethod(listener, on_progress, done, total);
        }
    });
    hd->last_sync  = r;
    hd->last_error = r.error;
    return to_jdoubles(env, { r.ok ? 1.0 : 0.0, (double) r.n_prompt, (double) r.n_reused, (double) r.n_prefilled,
                              r.restored_ckpt ? 1.0 : 0.0, r.full_reset ? 1.0 : 0.0, r.prefill_ms });
}

// fp: [temperature, topP, minP, repeatPenalty], ip: [maxTokens, topK, repeatLastN, seed]
// 반환: 생성 토큰. 통계는 nativeLastGenerate
JNIEXPORT jintArray JNICALL JFN(nativeGenerate)(JNIEnv * env, jclass, jlong h, jfloatArray fp, jintArray ip,
                                                jobject listener) {
    auto *              hd = H(h);
    std::vector<jfloat> fv(4);
    std::vector<jint>   iv(4);
    env->GetFloatArrayRegion(fp, 0, 4, fv.data());
    env->GetIntArrayRegion(ip, 0, 4, iv.data());
    ling::SamplerParams sp;
    sp.temperature    = fv[0];
    sp.top_p          = fv[1];
    sp.min_p          = fv[2];
    sp.repeat_penalty = fv[3];
    sp.top_k          = iv[1];
    sp.repeat_last_n  = iv[2];
    sp.seed           = (uint32_t) iv[3];

    jmethodID on_piece = nullptr;
    if (listener) {
        jclass cls = env->GetObjectClass(listener);
        on_piece   = env->GetMethodID(cls, "onPiece", "(Ljava/lang/String;I)Z");
        env->DeleteLocalRef(cls);
    }
    auto g = hd->engine.generate(iv[0], sp, [&](const std::string & piece, ling::token t) -> bool {
        if (!on_piece) {
            return true;
        }
        jstring s    = to_jstring(env, piece);
        jboolean ret = env->CallBooleanMethod(listener, on_piece, s, (jint) t);
        env->DeleteLocalRef(s);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return false;
        }
        return ret == JNI_TRUE;
    });
    hd->last_gen   = g;
    hd->last_error = g.error;
    return to_jints(env, g.tokens);
}

// 반환: [reason, decodeMs, ttftMs, nTokens]
JNIEXPORT jdoubleArray JNICALL JFN(nativeLastGenerate)(JNIEnv * env, jclass, jlong h) {
    const auto & g = H(h)->last_gen;
    return to_jdoubles(env, { (double) g.reason, g.decode_ms, g.ttft_ms, (double) g.tokens.size() });
}

JNIEXPORT jstring JNICALL JFN(nativeLastError)(JNIEnv * env, jclass, jlong h) {
    return to_jstring(env, H(h)->last_error);
}

JNIEXPORT void JNICALL JFN(nativeCancel)(JNIEnv *, jclass, jlong h) {
    H(h)->engine.cancel();
}

JNIEXPORT jboolean JNICALL JFN(nativeCheckpointNow)(JNIEnv *, jclass, jlong h) {
    return H(h)->engine.checkpoint_now() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JFN(nativeClearCheckpoints)(JNIEnv *, jclass, jlong h) {
    H(h)->engine.clear_checkpoints();
}

// 반환: [count, bytes, contextTokens, nCtx]
JNIEXPORT jlongArray JNICALL JFN(nativeState)(JNIEnv * env, jclass, jlong h) {
    auto &     e = H(h)->engine;
    jlong      v[4] = { (jlong) e.checkpoint_count(), (jlong) e.checkpoint_bytes(), (jlong) e.context_tokens().size(),
                        (jlong) e.n_ctx() };
    jlongArray a    = env->NewLongArray(4);
    env->SetLongArrayRegion(a, 0, 4, v);
    return a;
}

JNIEXPORT void JNICALL JFN(nativeReset)(JNIEnv *, jclass, jlong h) {
    H(h)->engine.reset();
}

// 반환: [ppTps, tgTps, ppMs, tgMs]
JNIEXPORT jdoubleArray JNICALL JFN(nativeBench)(JNIEnv * env, jclass, jlong h, jint np, jint ng, jint reps) {
    const auto b = H(h)->engine.bench(np, ng, reps);
    return to_jdoubles(env, { b.pp_tps, b.tg_tps, b.pp_ms, b.tg_ms });
}

// 정확성 검사: 마지막 위치 logits 의 log-softmax 상위 k개 [id0, lp0, id1, lp1, ...]
JNIEXPORT jfloatArray JNICALL JFN(nativeEvalTopK)(JNIEnv * env, jclass, jlong h, jintArray toks, jint k) {
    const auto logits = H(h)->engine.eval_logits(from_jints(env, toks));
    if (logits.empty()) {
        return env->NewFloatArray(0);
    }
    float mx = logits[0];
    for (float v : logits) mx = std::max(mx, v);
    double sum = 0;
    for (float v : logits) sum += std::exp((double) v - mx);
    const double lse = mx + std::log(sum);

    std::vector<int> idx(logits.size());
    for (size_t i = 0; i < idx.size(); ++i) idx[i] = (int) i;
    k = std::min<jint>(k, (jint) idx.size());
    std::partial_sort(idx.begin(), idx.begin() + k, idx.end(), [&](int a, int b) { return logits[a] > logits[b]; });
    std::vector<jfloat> out(2 * k);
    for (int i = 0; i < k; ++i) {
        out[2 * i]     = (jfloat) idx[i];
        out[2 * i + 1] = (jfloat) (logits[idx[i]] - lse);
    }
    jfloatArray a = env->NewFloatArray((jsize) out.size());
    env->SetFloatArrayRegion(a, 0, (jsize) out.size(), out.data());
    return a;
}

// [desc, sizeBytes, nParams, nVocab]
JNIEXPORT jobjectArray JNICALL JFN(nativeModelInfo)(JNIEnv * env, jclass, jlong h) {
    auto &                   e = H(h)->engine;
    std::vector<std::string> v = { e.model_desc(), std::to_string(e.model_size()), std::to_string(e.model_n_params()),
                                   std::to_string(e.n_vocab()) };
    jclass       str = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray((jsize) v.size(), str, nullptr);
    for (size_t i = 0; i < v.size(); ++i) {
        jstring s = to_jstring(env, v[i]);
        env->SetObjectArrayElement(out, (jsize) i, s);
        env->DeleteLocalRef(s);
    }
    return out;
}

}  // extern "C"
