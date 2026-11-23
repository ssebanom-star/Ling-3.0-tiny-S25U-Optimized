// NDK 없이 빌드하기 위한 런타임 스텁 (libc++/libc++abi 바이너리를 링크하지 않음)
#include <stddef.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdarg.h>
extern "C" {
void *malloc(size_t); void free(void *); void abort(void);
}
void *operator new(size_t n) { void *p = malloc(n ? n : 1); if (!p) abort(); return p; }
void *operator new[](size_t n) { return operator new(n); }
void operator delete(void *p) noexcept { free(p); }
void operator delete[](void *p) noexcept { free(p); }
void operator delete(void *p, size_t) noexcept { free(p); }
void operator delete[](void *p, size_t) noexcept { free(p); }
extern "C" {
// 함수 로컬 static 초기화 가드 (스레드 안전성은 단순 스핀)
int __cxa_guard_acquire(volatile uint64_t *g) {
    if (*(volatile uint8_t *)g) return 0;
    while (__atomic_exchange_n((volatile uint8_t *)g + 1, 1, __ATOMIC_ACQUIRE)) {}
    if (*(volatile uint8_t *)g) { __atomic_store_n((volatile uint8_t *)g + 1, 0, __ATOMIC_RELEASE); return 0; }
    return 1;
}
void __cxa_guard_release(volatile uint64_t *g) { *(volatile uint8_t *)g = 1; __atomic_store_n((volatile uint8_t *)g + 1, 0, __ATOMIC_RELEASE); }
void __cxa_guard_abort(volatile uint64_t *g) { __atomic_store_n((volatile uint8_t *)g + 1, 0, __ATOMIC_RELEASE); }
}
namespace std { inline namespace __ndk1 { [[noreturn]] void __libcpp_verbose_abort(const char *, ...) noexcept { abort(); } } }
