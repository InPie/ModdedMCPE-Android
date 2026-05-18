#include <android/log.h>
#include <cstdarg>
#include <cstring>
#include <jni.h>
#include <string>

#define LOG_TAG "EnderCore-GameStoragePatch-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::string g_externalStorageRoot;

static JNINativeInterface g_jniTable;
static JNIInvokeInterface g_vmTable;

static bool g_jniTableReady = false;
static bool g_vmTableReady = false;

static jmethodID g_getExternalStorageDirectory = nullptr;

static jobject (*old_CallStaticObjectMethodV)(JNIEnv*, jclass, jmethodID, va_list) = nullptr;
static jobject (*old_CallStaticObjectMethodA)(JNIEnv*, jclass, jmethodID, const jvalue*) = nullptr;

typedef jint (*AttachCurrentThreadFn)(JavaVM*, JNIEnv**, void*);
static AttachCurrentThreadFn old_AttachCurrentThread = nullptr;
static AttachCurrentThreadFn old_AttachCurrentThreadAsDaemon = nullptr;

extern "C" void game_storage_patch__set_external_storage_root(const char* path) {
    if (path && path[0]) {
        g_externalStorageRoot = path;
        LOGD("External storage root set to: %s", g_externalStorageRoot.c_str());
    } else {
        g_externalStorageRoot.clear();
        LOGE("External storage root is empty.");
    }
}

static void clearException(JNIEnv* env, const char* where) {
    if (env && env->ExceptionCheck()) {
        LOGE("JNI exception at %s", where);
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}

static void initIds(JNIEnv* env) {
    if (!env || g_getExternalStorageDirectory)
        return;

    jclass cls = env->FindClass("android/os/Environment");
    if (!cls) {
        clearException(env, "FindClass(android/os/Environment)");
        return;
    }

    g_getExternalStorageDirectory = env->GetStaticMethodID(
            cls,
            "getExternalStorageDirectory",
            "()Ljava/io/File;"
    );

    if (!g_getExternalStorageDirectory) {
        clearException(env, "GetStaticMethodID(Environment.getExternalStorageDirectory)");
    } else {
        LOGD("Cached Environment.getExternalStorageDirectory methodID=%p",
             g_getExternalStorageDirectory);
    }

    env->DeleteLocalRef(cls);
}

static jobject makeFile(JNIEnv* env, const char* path) {
    jclass fileClass = env->FindClass("java/io/File");
    if (!fileClass) {
        clearException(env, "FindClass(java/io/File)");
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
    if (!ctor) {
        clearException(env, "GetMethodID(File.<init>)");
        env->DeleteLocalRef(fileClass);
        return nullptr;
    }

    jstring jpath = env->NewStringUTF(path);
    if (!jpath) {
        clearException(env, "NewStringUTF");
        env->DeleteLocalRef(fileClass);
        return nullptr;
    }

    jobject file = env->NewObject(fileClass, ctor, jpath);
    if (!file)
        clearException(env, "NewObject(java.io.File)");

    env->DeleteLocalRef(jpath);
    env->DeleteLocalRef(fileClass);

    return file;
}

static bool shouldRedirect(JNIEnv* env, jmethodID methodID) {
    if (g_externalStorageRoot.empty())
        return false;

    initIds(env);

    return g_getExternalStorageDirectory
           && methodID == g_getExternalStorageDirectory;
}

static jobject redirectExternalStorage(JNIEnv* env) {
    LOGD("Redirect Environment.getExternalStorageDirectory() -> %s",
         g_externalStorageRoot.c_str());

    return makeFile(env, g_externalStorageRoot.c_str());
}

static jobject new_CallStaticObjectMethodV(
        JNIEnv* env,
        jclass clazz,
        jmethodID methodID,
        va_list args
) {
    if (shouldRedirect(env, methodID)) {
        jobject file = redirectExternalStorage(env);
        if (file)
            return file;
    }

    return old_CallStaticObjectMethodV
           ? old_CallStaticObjectMethodV(env, clazz, methodID, args)
           : nullptr;
}

static jobject new_CallStaticObjectMethodA(
        JNIEnv* env,
        jclass clazz,
        jmethodID methodID,
        const jvalue* args
) {
    if (shouldRedirect(env, methodID)) {
        jobject file = redirectExternalStorage(env);
        if (file)
            return file;
    }

    return old_CallStaticObjectMethodA
           ? old_CallStaticObjectMethodA(env, clazz, methodID, args)
           : nullptr;
}

static jobject new_CallStaticObjectMethod(
        JNIEnv* env,
        jclass clazz,
        jmethodID methodID,
        ...
) {
    va_list args;
    va_start(args, methodID);

    jobject result;
    if (shouldRedirect(env, methodID)) {
        result = redirectExternalStorage(env);
    } else {
        result = old_CallStaticObjectMethodV
                 ? old_CallStaticObjectMethodV(env, clazz, methodID, args)
                 : nullptr;
    }

    va_end(args);
    return result;
}

extern "C" void game_storage_patch_env(JNIEnv* env) {
    if (!env)
        return;

    if (!g_jniTableReady) {
        std::memcpy(&g_jniTable, env->functions, sizeof(JNINativeInterface));

        old_CallStaticObjectMethodV = g_jniTable.CallStaticObjectMethodV;
        old_CallStaticObjectMethodA = g_jniTable.CallStaticObjectMethodA;

        g_jniTable.CallStaticObjectMethod =
                reinterpret_cast<decltype(g_jniTable.CallStaticObjectMethod)>(
                        new_CallStaticObjectMethod
                );
        g_jniTable.CallStaticObjectMethodV =
                reinterpret_cast<decltype(g_jniTable.CallStaticObjectMethodV)>(
                        new_CallStaticObjectMethodV
                );
        g_jniTable.CallStaticObjectMethodA =
                reinterpret_cast<decltype(g_jniTable.CallStaticObjectMethodA)>(
                        new_CallStaticObjectMethodA
                );

        g_jniTableReady = true;
        LOGD("JNIEnv table prepared.");
    }

    env->functions = &g_jniTable;

    initIds(env);
}

static jint new_AttachCurrentThread(JavaVM* vm, JNIEnv** env, void* args) {
    if (!old_AttachCurrentThread)
        return JNI_ERR;

    jint result = old_AttachCurrentThread(vm, env, args);

    if (result == JNI_OK && env && *env)
        game_storage_patch_env(*env);

    return result;
}

static jint new_AttachCurrentThreadAsDaemon(JavaVM* vm, JNIEnv** env, void* args) {
    if (!old_AttachCurrentThreadAsDaemon)
        return JNI_ERR;

    jint result = old_AttachCurrentThreadAsDaemon(vm, env, args);

    if (result == JNI_OK && env && *env)
        game_storage_patch_env(*env);

    return result;
}

extern "C" void game_storage_patch_vm(JavaVM* vm) {
    if (!vm)
        return;

    if (!g_vmTableReady) {
        std::memcpy(&g_vmTable, vm->functions, sizeof(JNIInvokeInterface));

        old_AttachCurrentThread =
                reinterpret_cast<AttachCurrentThreadFn>(g_vmTable.AttachCurrentThread);
        old_AttachCurrentThreadAsDaemon =
                reinterpret_cast<AttachCurrentThreadFn>(g_vmTable.AttachCurrentThreadAsDaemon);

        g_vmTable.AttachCurrentThread =
                reinterpret_cast<decltype(g_vmTable.AttachCurrentThread)>(
                        new_AttachCurrentThread
                );
        g_vmTable.AttachCurrentThreadAsDaemon =
                reinterpret_cast<decltype(g_vmTable.AttachCurrentThreadAsDaemon)>(
                        new_AttachCurrentThreadAsDaemon
                );

        g_vmTableReady = true;
        LOGD("JavaVM table prepared.");
    }

    vm->functions = &g_vmTable;
}

extern "C" void game_storage_patch_init(JavaVM* vm) {
    if (!vm)
        return;

    game_storage_patch_vm(vm);

    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && env) {
        game_storage_patch_env(env);
    }
}
