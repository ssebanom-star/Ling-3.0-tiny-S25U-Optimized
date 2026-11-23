// libhvmvk.so — libvulkan.so 대체 shim.
// 앱의 dlopen("libvulkan.so") 문자열을 "libhvmvk.so" 로 바꿔 놓으면(scripts/apply_turnip.sh) 이 라이브러리가 로드되고,
// 여기서 libadrenotools 로 Turnip(사용자 Vulkan 드라이버) 이 올라간 libvulkan 핸들을 만들어 로더 진입점을 전달한다.
// 드라이버가 없거나 실패하면 시스템 libvulkan.so 로 폴백(동작 변화 없음).
//
// 환경변수 (앱의 "추가 환경변수"에 입력):
//   HVM_VK_DRIVER      "bundled" = APK 에 넣은 libvulkan_freedreno.so (nativeLibraryDir) 사용.
//                      그 외: 드라이버 폴더. 상대경로면 $HOME/drivers/<값>/ , 절대경로면 그대로. (미설정 = 시스템 드라이버)
//   HVM_VK_DRIVER_LIB  드라이버 .so 이름 (미설정 시 폴더의 meta.json "libraryName", 그것도 없으면 libvulkan_freedreno.so)
//   HVM_VK_REDIRECT    (선택) 드라이버 파일 접근 리다이렉트 폴더 (ADRENOTOOLS_DRIVER_FILE_REDIRECT)
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <string>
#include <android/log.h>
#include <adrenotools/driver.h>

#define TAG "HVMVK"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

static void *g_vk = nullptr;
static bool g_tried = false;

static std::string self_dir() {
    Dl_info info;
    if (dladdr((void *)&self_dir, &info) && info.dli_fname) {
        std::string p(info.dli_fname);
        auto s = p.rfind('/');
        if (s != std::string::npos) return p.substr(0, s);
    }
    return "";
}

static std::string read_lib_name(const std::string &dir) {
    std::string meta = dir + "meta.json";
    FILE *f = fopen(meta.c_str(), "r");
    if (!f) return "libvulkan_freedreno.so";
    char buf[4096]; size_t n = fread(buf, 1, sizeof(buf) - 1, f); fclose(f); buf[n] = 0;
    const char *k = strstr(buf, "\"libraryName\"");
    if (k) {
        k = strchr(k + 13, ':');
        if (k) { k = strchr(k, '"'); if (k) { const char *e = strchr(k + 1, '"'); if (e) return std::string(k + 1, e - k - 1); } }
    }
    return "libvulkan_freedreno.so";
}

static void *open_vk() {
    if (g_vk || g_tried) return g_vk;
    g_tried = true;
    const char *drv = getenv("HVM_VK_DRIVER");
    if (drv && *drv) {
        std::string dir = drv;
        if (dir == "bundled") dir = self_dir();
        else if (dir[0] != '/') { const char *home = getenv("HOME"); dir = std::string(home ? home : "") + "/drivers/" + dir; }
        if (dir.back() != '/') dir += '/';
        const char *ln = getenv("HVM_VK_DRIVER_LIB");
        std::string lib = (ln && *ln) ? ln : read_lib_name(dir);
        std::string hook = self_dir();
        const char *home = getenv("HOME");
        std::string tmp = std::string(home ? home : "/data/local/tmp") + "/vk_tmp/";
        mkdir(tmp.c_str(), 0700);
        const char *redir = getenv("HVM_VK_REDIRECT");
        int flags = ADRENOTOOLS_DRIVER_CUSTOM | ((redir && *redir) ? ADRENOTOOLS_DRIVER_FILE_REDIRECT : 0);
        LOGI("custom driver: dir=%s lib=%s hook=%s tmp=%s", dir.c_str(), lib.c_str(), hook.c_str(), tmp.c_str());
        g_vk = adrenotools_open_libvulkan(RTLD_NOW, flags, tmp.c_str(), (hook + "/").c_str(), dir.c_str(), lib.c_str(),
                                          (redir && *redir) ? redir : nullptr, nullptr);
        if (!g_vk) LOGW("adrenotools_open_libvulkan failed -> system libvulkan fallback");
    }
    if (!g_vk) g_vk = dlopen("libvulkan.so", RTLD_NOW);
    LOGI("libvulkan handle=%p (%s)", g_vk, (drv && *drv) ? "custom requested" : "system");
    return g_vk;
}

template <typename F> static F sym(const char *n) { void *h = open_vk(); return h ? (F)dlsym(h, n) : nullptr; }

#define EXPORT extern "C" __attribute__((visibility("default")))
EXPORT void *vkGetInstanceProcAddr(void *inst, const char *name) {
    auto f = sym<void *(*)(void *, const char *)>("vkGetInstanceProcAddr"); return f ? f(inst, name) : nullptr; }
EXPORT void *vkGetDeviceProcAddr(void *dev, const char *name) {
    auto f = sym<void *(*)(void *, const char *)>("vkGetDeviceProcAddr"); return f ? f(dev, name) : nullptr; }
EXPORT int vkCreateInstance(const void *ci, const void *alloc, void *out) {
    auto f = sym<int (*)(const void *, const void *, void *)>("vkCreateInstance"); return f ? f(ci, alloc, out) : -3; }
EXPORT int vkEnumerateInstanceExtensionProperties(const char *layer, uint32_t *n, void *p) {
    auto f = sym<int (*)(const char *, uint32_t *, void *)>("vkEnumerateInstanceExtensionProperties"); return f ? f(layer, n, p) : -3; }
EXPORT int vkEnumerateInstanceLayerProperties(uint32_t *n, void *p) {
    auto f = sym<int (*)(uint32_t *, void *)>("vkEnumerateInstanceLayerProperties"); return f ? f(n, p) : -3; }
EXPORT int vkEnumerateInstanceVersion(uint32_t *v) {
    auto f = sym<int (*)(uint32_t *)>("vkEnumerateInstanceVersion"); return f ? f(v) : -3; }
