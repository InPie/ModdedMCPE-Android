#include <jni.h>
#include <string>
#include <dlfcn.h>
#include <cstdio>
#include <cstring>
#include <android/log.h>
#include <android/native_activity.h>

#define LOG_TAG "EnderCore-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void (*android_main_minecraft)(struct android_app *app);

static void (*ANativeActivity_onCreate_minecraft)(ANativeActivity *activity, void *savedState,
                                                  size_t savedStateSize);

extern "C" void android_main(struct android_app *app) {
    LOGD("Forward android_main to mcpe");
    android_main_minecraft(app);
}

extern "C" void
ANativeActivity_onCreate(ANativeActivity *activity, void *savedState, size_t savedStateSize) {
    LOGD("Forward ANativeActivity_onCreate to mcpe");
    ANativeActivity_onCreate_minecraft(activity, savedState, savedStateSize);
}

static std::string findMinecraftLibraryPath() {
    FILE *maps = fopen("/proc/self/maps", "r");
    if (maps == nullptr)
        return "";

    char line[1024];
    while (fgets(line, sizeof(line), maps) != nullptr) {
        if (strstr(line, "libminecraftpe.so") == nullptr)
            continue;

        char *path = strchr(line, '/');
        if (path == nullptr)
            continue;

        size_t length = strlen(path);
        while (length > 0 && (path[length - 1] == '\n' || path[length - 1] == '\r')) {
            path[--length] = '\0';
        }
        fclose(maps);
        return std::string(path);
    }

    fclose(maps);
    return "";
}

static void *openMinecraftLibrary() {
    dlerror();
    void *handle = dlopen("libminecraftpe.so", RTLD_LAZY);
    if (handle != nullptr) {
        LOGD("Opened libminecraftpe.so by name.");
        return handle;
    }

    const char *nameError = dlerror();
    LOGD("Unable to open libminecraftpe.so by name: %s", nameError == nullptr ? "unknown error" : nameError);

    std::string path = findMinecraftLibraryPath();
    if (path.empty()) {
        LOGE("Unable to find loaded libminecraftpe.so in /proc/self/maps.");
        return nullptr;
    }

    dlerror();
    handle = dlopen(path.c_str(), RTLD_LAZY);
    if (handle != nullptr) {
        LOGD("Opened libminecraftpe.so by path: %s", path.c_str());
        return handle;
    }

    const char *pathError = dlerror();
    LOGE("Unable to open libminecraftpe.so by path %s: %s",
         path.c_str(), pathError == nullptr ? "unknown error" : pathError);
    return nullptr;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    void *handle = openMinecraftLibrary();
    android_main_minecraft = (void (*)(struct android_app *)) (dlsym(handle, "android_main"));
    ANativeActivity_onCreate_minecraft = (void (*)(ANativeActivity *, void *, size_t)) (dlsym(
            handle, "ANativeActivity_onCreate"));
    LOGD("Resolved symbols: android_main=%p, ANativeActivity_onCreate=%p",
         android_main_minecraft, ANativeActivity_onCreate_minecraft);
    return JNI_VERSION_1_6;
}