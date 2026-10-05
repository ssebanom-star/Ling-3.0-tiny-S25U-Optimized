// 호스트 JNI 테스트용 android/log.h 스텁
#pragma once
#include <cstdarg>
#include <cstdio>
enum { ANDROID_LOG_INFO = 4, ANDROID_LOG_WARN = 5, ANDROID_LOG_ERROR = 6 };
static inline int __android_log_print(int prio, const char * tag, const char * fmt, ...) {
    if (prio < ANDROID_LOG_WARN) return 0;
    va_list ap; va_start(ap, fmt);
    std::fprintf(stderr, "[%s] ", tag); int n = std::vfprintf(stderr, fmt, ap);
    va_end(ap); return n;
}
