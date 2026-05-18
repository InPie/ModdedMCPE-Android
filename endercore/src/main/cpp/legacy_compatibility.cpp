#include <android/log.h>
#include <pthread.h>
#include <vector>

#include "include/xhook.h"

#define LOG_TAG "EnderCore-LegacyCompat-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static int (*real_pthread_create)(pthread_t *thread, const pthread_attr_t *attr,
                                  void *(*start_routine)(void *), void *arg) = nullptr;
static int (*real_pthread_join)(pthread_t thread, void **retval) = nullptr;
static pthread_mutex_t g_detachedThreadsMutex = PTHREAD_MUTEX_INITIALIZER;
static std::vector<pthread_t> g_detachedMinecraftThreads;

static void trackDetachedMinecraftThread(pthread_t thread) {
    pthread_mutex_lock(&g_detachedThreadsMutex);
    for (pthread_t existing : g_detachedMinecraftThreads) {
        if (pthread_equal(existing, thread)) {
            pthread_mutex_unlock(&g_detachedThreadsMutex);
            return;
        }
    }
    g_detachedMinecraftThreads.push_back(thread);
    pthread_mutex_unlock(&g_detachedThreadsMutex);
}

static bool consumeDetachedMinecraftThread(pthread_t thread) {
    pthread_mutex_lock(&g_detachedThreadsMutex);
    for (auto it = g_detachedMinecraftThreads.begin(); it != g_detachedMinecraftThreads.end(); ++it) {
        if (pthread_equal(*it, thread)) {
            g_detachedMinecraftThreads.erase(it);
            pthread_mutex_unlock(&g_detachedThreadsMutex);
            return true;
        }
    }
    pthread_mutex_unlock(&g_detachedThreadsMutex);
    return false;
}

static int mcpe_pthread_create(pthread_t *thread, const pthread_attr_t *attr,
                               void *(*start_routine)(void *), void *arg) {
    if (real_pthread_create == nullptr)
        return -1;

    int result = real_pthread_create(thread, attr, start_routine, arg);
    if (result == 0 && thread != nullptr && attr != nullptr) {
        int detachState = PTHREAD_CREATE_JOINABLE;
        if (pthread_attr_getdetachstate(attr, &detachState) == 0 &&
            detachState == PTHREAD_CREATE_DETACHED) {
            trackDetachedMinecraftThread(*thread);
            LOGD("Tracked detached MCPE pthread: %lu",
                 static_cast<unsigned long>(*thread));
        }
    }
    return result;
}

static int mcpe_pthread_join(pthread_t thread, void **retval) {
    if (consumeDetachedMinecraftThread(thread)) {
        LOGD("Ignored pthread_join on detached MCPE pthread: %lu",
             static_cast<unsigned long>(thread));
        if (retval != nullptr)
            *retval = nullptr;
        return 0;
    }

    if (real_pthread_join == nullptr)
        return -1;

    return real_pthread_join(thread, retval);
}

extern "C" void legacy_compatibility_init() {
    xhook_enable_sigsegv_protection(1);

    int createResult = xhook_register(".*libminecraftpe\\.so$", "pthread_create",
                                      (void*) &mcpe_pthread_create,
                                      (void**) &real_pthread_create);
    int joinResult = xhook_register(".*libminecraftpe\\.so$", "pthread_join",
                                    (void*) &mcpe_pthread_join,
                                    (void**) &real_pthread_join);
    int refreshResult = xhook_refresh(0);

    LOGD("MCPE pthread compat hooks registered: pthread_create=%d, pthread_join=%d, refresh=%d",
         createResult, joinResult, refreshResult);
}
