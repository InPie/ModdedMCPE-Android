#include <jni.h>
#include <string>
#include <dlfcn.h>
#include <cstdio>
#include <cstring>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <android/native_activity.h>

#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdarg.h>
#include "include/xhook.h"

#define LOG_TAG "EnderCore-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::string g_instanceDataPath = "";

static std::string redirectPath(const char* pathname) {
    if (!pathname || g_instanceDataPath.empty()) return pathname ? pathname : "";
    const char* mojang = strstr(pathname, "games/com.mojang");
    if (mojang) {
        return g_instanceDataPath + "/" + mojang;
    }
    return pathname;
}

static int (*old_open)(const char *pathname, int flags, ...) = nullptr;
static int new_open(const char *pathname, int flags, ...) {
    std::string new_path = redirectPath(pathname);
    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode_t mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
        if (old_open) return old_open(new_path.c_str(), flags, mode);
    }
    if (old_open) return old_open(new_path.c_str(), flags);
    return -1;
}

static FILE* (*old_fopen)(const char *pathname, const char *mode) = nullptr;
static FILE* new_fopen(const char *pathname, const char *mode) {
    std::string new_path = redirectPath(pathname);
    if (old_fopen) return old_fopen(new_path.c_str(), mode);
    return nullptr;
}

static int (*old_mkdir)(const char *pathname, mode_t mode) = nullptr;
static int new_mkdir(const char *pathname, mode_t mode) {
    std::string new_path = redirectPath(pathname);
    if (old_mkdir) return old_mkdir(new_path.c_str(), mode);
    return -1;
}

static int (*old_stat)(const char *pathname, struct stat *statbuf) = nullptr;
static int new_stat(const char *pathname, struct stat *statbuf) {
    std::string new_path = redirectPath(pathname);
    if (old_stat) return old_stat(new_path.c_str(), statbuf);
    return -1;
}

static int (*old_access)(const char *pathname, int mode) = nullptr;
static int new_access(const char *pathname, int mode) {
    std::string new_path = redirectPath(pathname);
    if (old_access) return old_access(new_path.c_str(), mode);
    return -1;
}



static void (*android_main_minecraft)(struct android_app *app);

static void (*ANativeActivity_onCreate_minecraft)(ANativeActivity *activity, void *savedState,
                                                  size_t savedStateSize);

static void logNativeAssetProbe(AAssetManager *assetManager, const char *path) {
    if (assetManager == nullptr) {
        LOGE("Native asset probe failed: assetManager is null, path=%s", path);
        return;
    }

    AAsset *asset = AAssetManager_open(assetManager, path, AASSET_MODE_BUFFER);
    if (asset == nullptr) {
        LOGE("Native asset probe failed: %s", path);
        return;
    }

    off_t length = AAsset_getLength(asset);
    LOGD("Native asset probe OK: %s, length=%ld", path, static_cast<long>(length));
    AAsset_close(asset);
}

static void logNativeAssetState(ANativeActivity *activity) {
    AAssetManager *assetManager = activity == nullptr ? nullptr : activity->assetManager;
    LOGD("Native Activity assetManager=%p", assetManager);
    logNativeAssetProbe(assetManager, "images/font/default8.png");
    logNativeAssetProbe(assetManager, "images/gui/title.png");
    logNativeAssetProbe(assetManager, "resourcepacks/vanilla/models/mobs.json");
    logNativeAssetProbe(assetManager, "skins/skins.json");
    logNativeAssetProbe(assetManager, "images/mob/skins/Base/Vanilla.json");
    logNativeAssetProbe(assetManager, "skins/Base/base.json");
}

extern "C" void android_main(struct android_app *app) {
    LOGD("Forward android_main to mcpe");
    android_main_minecraft(app);
}

static void patchNativeActivity(ANativeActivity *activity) {
    JNIEnv *env = activity->env;
    jobject activityObj = activity->clazz;

    jclass activityClass = env->GetObjectClass(activityObj);

    // patch AssetManager
    jmethodID getAssetManagerMethod = env->GetMethodID(activityClass, "getPatchAssetManager", "()Landroid/content/res/AssetManager;");
    if (getAssetManagerMethod) {
        jobject assetManagerObj = env->CallObjectMethod(activityObj, getAssetManagerMethod);
        if (assetManagerObj) {
            AAssetManager *patchedManager = AAssetManager_fromJava(env, assetManagerObj);
            if (patchedManager) {
                LOGD("Patching ANativeActivity->assetManager from %p to %p", activity->assetManager, patchedManager);
                activity->assetManager = patchedManager;
            }
        }
    } else {
        LOGE("Failed to find getPatchAssetManager method");
    }

    // patch internal data path
    jmethodID getInternalPathMethod = env->GetMethodID(activityClass, "getPatchInternalDataPath", "()Ljava/lang/String;");
    if (getInternalPathMethod) {
        jstring pathStr = (jstring)env->CallObjectMethod(activityObj, getInternalPathMethod);
        if (pathStr) {
            const char *path = env->GetStringUTFChars(pathStr, nullptr);
            LOGD("Patching ANativeActivity->internalDataPath to %s", path);
            activity->internalDataPath = strdup(path);
            g_instanceDataPath = path;
            env->ReleaseStringUTFChars(pathStr, path);
        }
    }

    // patch external data path
    jmethodID getExternalPathMethod = env->GetMethodID(activityClass, "getPatchExternalDataPath", "()Ljava/lang/String;");
    if (getExternalPathMethod) {
        jstring pathStr = (jstring)env->CallObjectMethod(activityObj, getExternalPathMethod);
        if (pathStr) {
            const char *path = env->GetStringUTFChars(pathStr, nullptr);
            LOGD("Patching ANativeActivity->externalDataPath to %s", path);
            activity->externalDataPath = strdup(path);
            env->ReleaseStringUTFChars(pathStr, path);
        }
    }
    
    env->DeleteLocalRef(activityClass);
    xhook_refresh(0);
}

extern "C" void
ANativeActivity_onCreate(ANativeActivity *activity, void *savedState, size_t savedStateSize) {
    LOGD("Forward ANativeActivity_onCreate to mcpe (v3)");
    patchNativeActivity(activity);
    logNativeAssetState(activity);
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
    // init libc function pointers
    old_open = (int (*)(const char*, int, ...))dlsym(RTLD_NEXT, "open");
    old_fopen = (FILE* (*)(const char*, const char*))dlsym(RTLD_NEXT, "fopen");
    old_mkdir = (int (*)(const char*, mode_t))dlsym(RTLD_NEXT, "mkdir");
    old_stat = (int (*)(const char*, struct stat*))dlsym(RTLD_NEXT, "stat");
    old_access = (int (*)(const char*, int))dlsym(RTLD_NEXT, "access");

    // xhook for libc IO funcs
    xhook_register(".*libminecraftpe\\.so$", "open", (void*)new_open, (void**)&old_open);
    xhook_register(".*libminecraftpe\\.so$", "fopen", (void*)new_fopen, (void**)&old_fopen);
    xhook_register(".*libminecraftpe\\.so$", "mkdir", (void*)new_mkdir, (void**)&old_mkdir);
    xhook_register(".*libminecraftpe\\.so$", "stat", (void*)new_stat, (void**)&old_stat);
    xhook_register(".*libminecraftpe\\.so$", "access", (void*)new_access, (void**)&old_access);
    //xhook_refresh(0);

    void *handle = openMinecraftLibrary();
    if (handle) {
        android_main_minecraft = (void (*)(struct android_app *)) (dlsym(handle, "android_main"));
        ANativeActivity_onCreate_minecraft = (void (*)(ANativeActivity *, void *, size_t)) (dlsym(
                handle, "ANativeActivity_onCreate"));
        LOGD("Resolved symbols: android_main=%p, ANativeActivity_onCreate=%p",
             android_main_minecraft, ANativeActivity_onCreate_minecraft);
    } else {
        LOGE("Failed to open libminecraftpe.so");
    }
    
    return JNI_VERSION_1_6;
}