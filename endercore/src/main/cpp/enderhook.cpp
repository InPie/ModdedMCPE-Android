#include "enderhook.h"

#include <android/log.h>
#include <cstdarg>
#include <cstring>
#include <pthread.h>
#include <string>

#define LOG_TAG "EnderHook-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static pthread_mutex_t g_hookMutex = PTHREAD_MUTEX_INITIALIZER;

static std::string g_externalStorageRoot;

// Copied JNI tables.
static JNINativeInterface g_jniTable;
static JNIInvokeInterface g_vmTable;

static bool g_jniTableReady = false;
static bool g_vmTableReady = false;

// Original JNIEnv functions.
static jobject (*old_CallStaticObjectMethod)(
        JNIEnv* env,
        jclass clazz,
        jmethodID methodID,
        ...
) = nullptr;

static jobject (*old_CallStaticObjectMethodV)(
        JNIEnv* env,
        jclass clazz,
        jmethodID methodID,
        va_list args
) = nullptr;

static jobject (*old_CallStaticObjectMethodA)(
        JNIEnv* env,
        jclass clazz,
        jmethodID methodID,
        const jvalue* args
) = nullptr;

// JavaVM function pointer type
typedef jint (*AttachCurrentThreadFn)(JavaVM* vm, JNIEnv** env, void* args);
typedef jint (*AttachCurrentThreadAsDaemonFn)(JavaVM* vm, JNIEnv** env, void* args);

static AttachCurrentThreadFn old_AttachCurrentThread = nullptr;
static AttachCurrentThreadAsDaemonFn old_AttachCurrentThreadAsDaemon = nullptr;

static jmethodID g_getExternalStorageDirectory = nullptr;

static bool hasExternalStorageRoot() {
    return !g_externalStorageRoot.empty();
}

void enderhook_set_external_storage_root(const char* path) {
    pthread_mutex_lock(&g_hookMutex);

    if (path && path[0] != '\0') {
        g_externalStorageRoot = path;
        LOGD("External storage root set to: %s", g_externalStorageRoot.c_str());
    } else {
        g_externalStorageRoot.clear();
        LOGE("External storage root cleared or invalid.");
    }

    pthread_mutex_unlock(&g_hookMutex);
}

static std::string getExternalStorageRootCopy() {
    pthread_mutex_lock(&g_hookMutex);
    std::string copy = g_externalStorageRoot;
    pthread_mutex_unlock(&g_hookMutex);
    return copy;
}

static void clearPendingException(JNIEnv* env, const char* where) {
    if (env != nullptr && env->ExceptionCheck()) {
        LOGE("JNI exception at %s", where);
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}

static void initMethodIds(JNIEnv* env) {
    if (env == nullptr || g_getExternalStorageDirectory != nullptr)
        return;

    jclass environmentClass = env->FindClass("android/os/Environment");
    if (environmentClass == nullptr) {
        clearPendingException(env, "FindClass(android/os/Environment)");
        return;
    }

    g_getExternalStorageDirectory = env->GetStaticMethodID(
            environmentClass,
            "getExternalStorageDirectory",
            "()Ljava/io/File;"
    );

    if (g_getExternalStorageDirectory == nullptr) {
        clearPendingException(env, "GetStaticMethodID(Environment.getExternalStorageDirectory)");
    } else {
        LOGD("Cached Environment.getExternalStorageDirectory methodID=%p",
             g_getExternalStorageDirectory);
    }

    env->DeleteLocalRef(environmentClass);
}

static bool shouldRedirectGetExternalStorageDirectory(JNIEnv* env, jmethodID methodID) {
    if (!hasExternalStorageRoot())
        return false;

    initMethodIds(env);

    return g_getExternalStorageDirectory != nullptr
           && methodID == g_getExternalStorageDirectory;
}

static jobject createJavaFile(JNIEnv* env, const char* path) {
    if (env == nullptr || path == nullptr || path[0] == '\0')
        return nullptr;

    jclass fileClass = env->FindClass("java/io/File");
    if (fileClass == nullptr) {
        clearPendingException(env, "FindClass(java/io/File)");
        return nullptr;
    }

    jmethodID fileCtor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
    if (fileCtor == nullptr) {
        clearPendingException(env, "GetMethodID(File.<init>(String))");
        env->DeleteLocalRef(fileClass);
        return nullptr;
    }

    jstring pathString = env->NewStringUTF(path);
    if (pathString == nullptr) {
        clearPendingException(env, "NewStringUTF(externalStorageRoot)");
        env->DeleteLocalRef(fileClass);
        return nullptr;
    }

    jobject file = env->NewObject(fileClass, fileCtor, pathString);
    if (file == nullptr) {
        clearPendingException(env, "NewObject(java.io.File)");
    }

    env->DeleteLocalRef(pathString);
    env->DeleteLocalRef(fileClass);

    return file;
}

static jobject makeRedirectedExternalStorageFile(JNIEnv* env) {
    std::string root = getExternalStorageRootCopy();

    if (root.empty()) {
        LOGE("Environment.getExternalStorageDirectory redirect requested, but root is empty.");
        return nullptr;
    }

    LOGD("Redirect Environment.getExternalStorageDirectory() -> %s", root.c_str());
    return createJavaFile(env, root.c_str());
}

static jobject new_CallStaticObjectMethod(
        JNIEnv* env,
        jclass clazz,
        jmethodID methodID,
        ...
) {
    if (shouldRedirectGetExternalStorageDirectory(env, methodID)) {
        jobject redirected = makeRedirectedExternalStorageFile(env);
        if (redirected != nullptr)
            return redirected;
    }

    va_list args;
    va_start(args, methodID);

    jobject result = nullptr;
    if (old_CallStaticObjectMethodV != nullptr) {
        result = old_CallStaticObjectMethodV(env, clazz, methodID, args);
    } else {
        LOGE("old_CallStaticObjectMethodV is null.");
    }

    va_end(args);
    return result;
}

static jobject new_CallStaticObjectMethodV(
        JNIEnv* env,
        jclass clazz,
        jmethodID methodID,
        va_list args
) {
    if (shouldRedirectGetExternalStorageDirectory(env, methodID)) {
        jobject redirected = makeRedirectedExternalStorageFile(env);
        if (redirected != nullptr)
            return redirected;
    }

    if (old_CallStaticObjectMethodV == nullptr) {
        LOGE("old_CallStaticObjectMethodV is null.");
        return nullptr;
    }

    return old_CallStaticObjectMethodV(env, clazz, methodID, args);
}

static jobject new_CallStaticObjectMethodA(
        JNIEnv* env,
        jclass clazz,
        jmethodID methodID,
        const jvalue* args
) {
    if (shouldRedirectGetExternalStorageDirectory(env, methodID)) {
        jobject redirected = makeRedirectedExternalStorageFile(env);
        if (redirected != nullptr)
            return redirected;
    }

    if (old_CallStaticObjectMethodA == nullptr) {
        LOGE("old_CallStaticObjectMethodA is null.");
        return nullptr;
    }

    return old_CallStaticObjectMethodA(env, clazz, methodID, args);
}

void enderhook_patch_env(JNIEnv* env) {
    if (env == nullptr)
        return;

    pthread_mutex_lock(&g_hookMutex);

    if (!g_jniTableReady) {
        std::memcpy(&g_jniTable, env->functions, sizeof(JNINativeInterface));

        old_CallStaticObjectMethod = reinterpret_cast<decltype(old_CallStaticObjectMethod)>(
                g_jniTable.CallStaticObjectMethod
        );
        old_CallStaticObjectMethodV = reinterpret_cast<decltype(old_CallStaticObjectMethodV)>(
                g_jniTable.CallStaticObjectMethodV
        );
        old_CallStaticObjectMethodA = reinterpret_cast<decltype(old_CallStaticObjectMethodA)>(
                g_jniTable.CallStaticObjectMethodA
        );

        g_jniTable.CallStaticObjectMethod = reinterpret_cast<decltype(g_jniTable.CallStaticObjectMethod)>(
                new_CallStaticObjectMethod
        );
        g_jniTable.CallStaticObjectMethodV = reinterpret_cast<decltype(g_jniTable.CallStaticObjectMethodV)>(
                new_CallStaticObjectMethodV
        );
        g_jniTable.CallStaticObjectMethodA = reinterpret_cast<decltype(g_jniTable.CallStaticObjectMethodA)>(
                new_CallStaticObjectMethodA
        );

        g_jniTableReady = true;

        LOGD("JNIEnv table prepared.");
    }

    env->functions = &g_jniTable;

    pthread_mutex_unlock(&g_hookMutex);

    initMethodIds(env);

    LOGD("JNIEnv patched: %p", env);
}

static jint new_AttachCurrentThread(JavaVM* vm, JNIEnv** env, void* args) {
    if (old_AttachCurrentThread == nullptr) {
        LOGE("old_AttachCurrentThread is null.");
        return JNI_ERR;
    }

    jint result = old_AttachCurrentThread(vm, env, args);

    if (result == JNI_OK && env != nullptr && *env != nullptr) {
        enderhook_patch_env(*env);
    }

    return result;
}

static jint new_AttachCurrentThreadAsDaemon(JavaVM* vm, JNIEnv** env, void* args) {
    if (old_AttachCurrentThreadAsDaemon == nullptr) {
        LOGE("old_AttachCurrentThreadAsDaemon is null.");
        return JNI_ERR;
    }

    jint result = old_AttachCurrentThreadAsDaemon(vm, env, args);

    if (result == JNI_OK && env != nullptr && *env != nullptr) {
        enderhook_patch_env(*env);
    }

    return result;
}

void enderhook_patch_vm(JavaVM* vm) {
    if (vm == nullptr)
        return;

    pthread_mutex_lock(&g_hookMutex);

    if (!g_vmTableReady) {
        std::memcpy(&g_vmTable, vm->functions, sizeof(JNIInvokeInterface));

        old_AttachCurrentThread = reinterpret_cast<AttachCurrentThreadFn>(
                g_vmTable.AttachCurrentThread
        );
        old_AttachCurrentThreadAsDaemon = reinterpret_cast<AttachCurrentThreadAsDaemonFn>(
                g_vmTable.AttachCurrentThreadAsDaemon
        );

        g_vmTable.AttachCurrentThread = reinterpret_cast<decltype(g_vmTable.AttachCurrentThread)>(
                new_AttachCurrentThread
        );
        g_vmTable.AttachCurrentThreadAsDaemon = reinterpret_cast<decltype(g_vmTable.AttachCurrentThreadAsDaemon)>(
                new_AttachCurrentThreadAsDaemon
        );

        g_vmTableReady = true;

        LOGD("JavaVM table prepared.");
    }

    vm->functions = &g_vmTable;

    pthread_mutex_unlock(&g_hookMutex);

    LOGD("JavaVM patched: %p", vm);
}

void enderhook_init(JavaVM* vm) {
    if (vm == nullptr)
        return;

    enderhook_patch_vm(vm);

    JNIEnv* env = nullptr;
    jint getEnvResult = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);

    if (getEnvResult == JNI_OK && env != nullptr) {
        enderhook_patch_env(env);
    } else {
        LOGD("JNIEnv is not available in enderhook_init, result=%d. Will patch after AttachCurrentThread.", getEnvResult);
    }
}