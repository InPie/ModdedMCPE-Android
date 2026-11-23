#include <jni.h>
#include <string>
#include <fstream>
#include <dlfcn.h>
#include <android/log.h>
#include <android/native_activity.h>

#include "include/xhook.h"

#define LOG_TAG "EnderCore-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Patches of the game from the other files of this library, described where they are defined
void game_storage_patch_init(JavaVM* vm);
void game_storage_patch_activity(ANativeActivity* activity, void* gameLibrary);
void legacy_compatibility_register_hooks();
void game_resolution_patch_register_hooks();
void game_resolution_patch_activity(ANativeActivity* activity);
void game_resolution_patch_callbacks(ANativeActivity* activity);

JavaVM *g_jvm = nullptr;
jclass g_crashHandlerClass = nullptr;
jmethodID g_onNativeCrashMethod = nullptr;
void* g_mcpeHandle = nullptr;

// Entry points of the game. Versions on NativeActivity export ANativeActivity_onCreate, and up to 1.16
// android_main as well. Versions on GameActivity (1.21.101+) have neither and start through their own
// GameActivity_onCreate, so both stay null for them.
static void (*android_main_minecraft)(struct android_app *app);

static void (*ANativeActivity_onCreate_minecraft)(ANativeActivity *activity, void *savedState,
                                                  size_t savedStateSize);

extern "C" void android_main(struct android_app *app) {
    if (!android_main_minecraft) {
        LOGE("The game has no android_main.");
        return;
    }
    LOGD("Forward android_main to mcpe");
    android_main_minecraft(app);
}

// NativeActivity calls this in libendercore.so, the activity's library in the manifest, and the game's
// own onCreate runs from here. The patches read the activity before it and its callbacks after it.
extern "C" void
ANativeActivity_onCreate(ANativeActivity *activity, void *savedState, size_t savedStateSize) {
    if (!ANativeActivity_onCreate_minecraft) {
        LOGE("The game has no ANativeActivity_onCreate.");
        ANativeActivity_finish(activity);
        return;
    }
    LOGD("Forward ANativeActivity_onCreate to mcpe");
    game_storage_patch_activity(activity, g_mcpeHandle);
    game_resolution_patch_activity(activity);
    ANativeActivity_onCreate_minecraft(activity, savedState, savedStateSize);
    game_resolution_patch_callbacks(activity);
}

static std::string findMinecraftLibraryPath() {
    std::ifstream maps("/proc/self/maps");
    if (!maps.is_open()) return "";

    std::string line;
    while (std::getline(maps, line)) {
        if (line.find("libminecraftpe.so") != std::string::npos) {
            // start of the absolute path
            size_t slashPos = line.find('/');
            if (slashPos != std::string::npos) {
                return line.substr(slashPos);
            }
        }
    }
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
    using AndroidMainType = void (*)(struct android_app *);
    using ANativeActivityOnCreateType = void (*)(ANativeActivity *, void *, size_t);

    g_jvm = vm;
    game_storage_patch_init(vm);

    // xhook patches the imports of a library on the first refresh that finds it and doesn't come back
    // to it later. So all hooks are registered before this one refresh.
    xhook_enable_sigsegv_protection(1);
    legacy_compatibility_register_hooks();
    game_resolution_patch_register_hooks();
    if (xhook_refresh(0) != 0) {
        LOGE("Failed to hook libminecraftpe.so");
    }

    void *handle = openMinecraftLibrary();
    if (handle) {
        g_mcpeHandle = handle;

        android_main_minecraft = reinterpret_cast<AndroidMainType>(
                dlsym(handle, "android_main")
        );

        ANativeActivity_onCreate_minecraft = reinterpret_cast<ANativeActivityOnCreateType>(
                dlsym(handle, "ANativeActivity_onCreate")
        );

        LOGD("Resolved symbols: android_main=%p, ANativeActivity_onCreate=%p",
             android_main_minecraft, ANativeActivity_onCreate_minecraft);

        if (!ANativeActivity_onCreate_minecraft && !dlsym(handle, "GameActivity_onCreate")) {
            LOGE("libminecraftpe.so has neither ANativeActivity_onCreate nor GameActivity_onCreate.");
            return JNI_ERR;
        }
    } else {
        LOGE("Failed to open libminecraftpe.so");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}