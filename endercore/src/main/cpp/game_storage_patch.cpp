// Instance folders for the game code that finds its folders without asking Context. Java code and
// NativeActivity get them from GameStorageContext, the rest is here:
//  - 0.6 - 0.10 ask Environment.getExternalStorageDirectory() through JNI (0.11 already asks the activity
//    for getExternalStoragePath). Their JNI calls go through a patched function table, see patchEnv.
//  - 1.0 - 1.17 build paths of their files from AppPlatform_android::ANDROID_APPDATA_PATH,
//    see setAppDataPath.

#include <android/log.h>
#include <android/native_activity.h>
#include <jni.h>
#include <cstdarg>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <string>

#define LOG_TAG "EnderCore-GameStoragePatch-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char* const APPDATA_PATH_SYMBOL = "_ZN19AppPlatform_android20ANDROID_APPDATA_PATHE";
// std::string::assign(const char*) in libc++_shared.so, which 1.16+ link
static const char* const LIBCXX_STRING_ASSIGN_SYMBOL =
        "_ZNSt6__ndk112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEE6assignEPKc";

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

// Logs and clears the Java exception a JNI call left, since the next JNI call must not find one pending
static void clearException(JNIEnv* env, const char* where) {
    if (env && env->ExceptionCheck()) {
        LOGE("JNI exception at %s", where);
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}

// Finds Environment.getExternalStorageDirectory once: the game's calls to it are told by its method id
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
    }

    env->DeleteLocalRef(cls);
}

// new java.io.File(path), or null if it can't be made
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

// True for a call of Environment.getExternalStorageDirectory once the instance folder is known
static bool shouldRedirect(JNIEnv* env, jmethodID methodID) {
    if (g_externalStorageRoot.empty())
        return false;

    initIds(env);

    return g_getExternalStorageDirectory
           && methodID == g_getExternalStorageDirectory;
}

// The instance's external/ folder, which the game gets as its shared storage
static jobject redirectExternalStorage(JNIEnv* env) {
    return makeFile(env, g_externalStorageRoot.c_str());
}

// The game calls static Java methods returning an object through these three. A call of
// Environment.getExternalStorageDirectory() gets the instance's external/ folder, any other call goes
// to the real JNI function.
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

// Gives this thread's JNIEnv a copy of the JNI function table with the three calls above replaced.
// The copy is made once and is shared by all threads.
static void patchEnv(JNIEnv* env) {
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
    }

    env->functions = &g_jniTable;

    initIds(env);
}

// Threads the game attaches to Java later get the patched table as well
static jint new_AttachCurrentThread(JavaVM* vm, JNIEnv** env, void* args) {
    if (!old_AttachCurrentThread)
        return JNI_ERR;

    jint result = old_AttachCurrentThread(vm, env, args);

    if (result == JNI_OK && env && *env)
        patchEnv(*env);

    return result;
}

static jint new_AttachCurrentThreadAsDaemon(JavaVM* vm, JNIEnv** env, void* args) {
    if (!old_AttachCurrentThreadAsDaemon)
        return JNI_ERR;

    jint result = old_AttachCurrentThreadAsDaemon(vm, env, args);

    if (result == JNI_OK && env && *env)
        patchEnv(*env);

    return result;
}

// Replaces AttachCurrentThread and AttachCurrentThreadAsDaemon of the JavaVM, so every thread attached
// from now on gets the patched JNIEnv
static void patchVm(JavaVM* vm) {
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
    }

    vm->functions = &g_vmTable;
}

// Absolute path of the java.io.File that a method of the activity returns. The method takes no arguments
// or a String, which gets null. Empty if there is no such method or it returned no file.
static std::string getActivityPath(JNIEnv* env, jobject activity, const char* method, const char* signature,
                                   bool takesString) {
    std::string path;
    jclass activityClass = env->GetObjectClass(activity);
    jmethodID getter = env->GetMethodID(activityClass, method, signature);
    jobject file = nullptr;
    if (getter) {
        file = takesString ? env->CallObjectMethod(activity, getter, static_cast<jstring>(nullptr))
                           : env->CallObjectMethod(activity, getter);
    }
    clearException(env, method);

    if (file) {
        jclass fileClass = env->GetObjectClass(file);
        jmethodID getAbsolutePath = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
        jstring jpath = getAbsolutePath
                        ? static_cast<jstring>(env->CallObjectMethod(file, getAbsolutePath)) : nullptr;
        clearException(env, "File.getAbsolutePath");
        if (jpath) {
            const char* chars = env->GetStringUTFChars(jpath, nullptr);
            path = chars;
            env->ReleaseStringUTFChars(jpath, chars);
            env->DeleteLocalRef(jpath);
        }
        env->DeleteLocalRef(fileClass);
        env->DeleteLocalRef(file);
    }
    env->DeleteLocalRef(activityClass);
    return path;
}

// GNU STL (1.0 - 1.5) keeps a std::string as a pointer to its characters, which follow this header.
// A negative reference count marks the characters as never shared.
struct GnuStlStringHeader {
    size_t length;
    size_t capacity;
    int refcount;
};

// Sets AppPlatform_android::ANDROID_APPDATA_PATH to the instance's data/ folder, so the game keeps
// its com.mojang.minecraftpe folder there. The string is written in the layout of the C++ library
// the game was built with: 1.16+ link libc++_shared.so and get its own std::string::assign,
// 1.0 - 1.5 have GNU STL built in and get a new block of characters with its header.
static void setAppDataPath(void* gameLibrary, const std::string& dataDir) {
    void* appDataPath = gameLibrary ? dlsym(gameLibrary, APPDATA_PATH_SYMBOL) : nullptr;
    if (!appDataPath) {
        LOGD("The game has no ANDROID_APPDATA_PATH.");
        return;
    }
    std::string value = dataDir + "/";

    typedef void* (*StringAssign)(void*, const char*);
    StringAssign assign = reinterpret_cast<StringAssign>(dlsym(gameLibrary, LIBCXX_STRING_ASSIGN_SYMBOL));
    if (assign) {
        assign(appDataPath, value.c_str());
        LOGD("ANDROID_APPDATA_PATH (libc++): %s", value.c_str());
        return;
    }

    char* block = static_cast<char*>(malloc(sizeof(GnuStlStringHeader) + value.size() + 1));
    GnuStlStringHeader* header = reinterpret_cast<GnuStlStringHeader*>(block);
    header->length = value.size();
    header->capacity = value.size();
    header->refcount = -1;
    char* chars = block + sizeof(GnuStlStringHeader);
    memcpy(chars, value.c_str(), value.size() + 1);
    *reinterpret_cast<char**>(appDataPath) = chars;
    LOGD("ANDROID_APPDATA_PATH (GNU STL): %s", chars);
}

// Called from JNI_OnLoad: patches the JNI of the thread that loads the library and of the threads
// attached after it
void game_storage_patch_init(JavaVM* vm) {
    if (!vm)
        return;

    patchVm(vm);

    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && env) {
        patchEnv(env);
    }
}

// Called before ANativeActivity_onCreate of the game. Reads the instance folders from the activity,
// which gets them from GameStorageContext, and points the game to them.
void game_storage_patch_activity(ANativeActivity* activity, void* gameLibrary) {
    JNIEnv* env = activity->env;
    g_externalStorageRoot = getActivityPath(env, activity->clazz, "getExternalFilesDir",
                                            "(Ljava/lang/String;)Ljava/io/File;", true);
    LOGD("Shared storage: %s", g_externalStorageRoot.c_str());

    std::string dataDir = getActivityPath(env, activity->clazz, "getDataDir", "()Ljava/io/File;", false);
    if (dataDir.empty()) {
        LOGE("The activity has no data folder.");
    } else {
        setAppDataPath(gameLibrary, dataDir);
    }

    patchVm(activity->vm);
    patchEnv(env);
}
