// Lower resolution for game versions on NativeActivity (up to 1.19). The game draws into smaller
// window buffers, and the system stretches them over the window. Touches and the content rect shrink
// by the same factor, so they land where the game drew things. The sizes and DPI that the game asks
// Java for are shrunk by AgentMainActivity.

#include <android/input.h>
#include <android/log.h>
#include <android/native_activity.h>
#include <android/native_window.h>
#include <algorithm>
#include <cmath>

#include "include/xhook.h"

#define LOG_TAG "EnderCore-Resolution-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char* const GAME_LIBRARY = ".*libminecraftpe\\.so$";

// Share of the window size the game draws at, 1 at full resolution
static float g_scale = 1.0f;

static int32_t (*real_ANativeWindow_setBuffersGeometry)(ANativeWindow*, int32_t, int32_t, int32_t) = nullptr;
static float (*real_AMotionEvent_getX)(const AInputEvent*, size_t) = nullptr;
static float (*real_AMotionEvent_getY)(const AInputEvent*, size_t) = nullptr;
static float (*real_AMotionEvent_getRawX)(const AInputEvent*, size_t) = nullptr;
static float (*real_AMotionEvent_getRawY)(const AInputEvent*, size_t) = nullptr;
static float (*real_AMotionEvent_getAxisValue)(const AInputEvent*, int32_t, size_t) = nullptr;
static void (*game_onContentRectChanged)(ANativeActivity*, const ARect*) = nullptr;

// Window pixels to game pixels
static int32_t scaled(int32_t value) {
    return static_cast<int32_t>(lroundf(static_cast<float>(value) * g_scale));
}

// The game asks for buffers of the window size (0 x 0) and gets the window size times the scale.
// A size given explicitly shrinks the same way. The window size is read after setting 0 x 0 again:
// while a buffer size is set, the window reports that size and not its own.
static int32_t scaledSetBuffersGeometry(ANativeWindow* window, int32_t width, int32_t height, int32_t format) {
    if (g_scale == 1.0f) {
        return real_ANativeWindow_setBuffersGeometry(window, width, height, format);
    }
    if (width == 0 || height == 0) {
        real_ANativeWindow_setBuffersGeometry(window, 0, 0, format);
        width = ANativeWindow_getWidth(window);
        height = ANativeWindow_getHeight(window);
    }
    int32_t scaledWidth = std::max(1, scaled(width));
    int32_t scaledHeight = std::max(1, scaled(height));
    LOGD("Window buffers %dx%d for a window of %dx%d", scaledWidth, scaledHeight, width, height);
    return real_ANativeWindow_setBuffersGeometry(window, scaledWidth, scaledHeight, format);
}

// Touch positions come in window pixels, and the game gets them in its own
static float scaledGetX(const AInputEvent* event, size_t pointerIndex) {
    return real_AMotionEvent_getX(event, pointerIndex) * g_scale;
}

static float scaledGetY(const AInputEvent* event, size_t pointerIndex) {
    return real_AMotionEvent_getY(event, pointerIndex) * g_scale;
}

static float scaledGetRawX(const AInputEvent* event, size_t pointerIndex) {
    return real_AMotionEvent_getRawX(event, pointerIndex) * g_scale;
}

static float scaledGetRawY(const AInputEvent* event, size_t pointerIndex) {
    return real_AMotionEvent_getRawY(event, pointerIndex) * g_scale;
}

// Of all axes only X and Y are a position. Pressure, touch size and the rest stay as they are.
static float scaledGetAxisValue(const AInputEvent* event, int32_t axis, size_t pointerIndex) {
    float value = real_AMotionEvent_getAxisValue(event, axis, pointerIndex);
    return axis == AMOTION_EVENT_AXIS_X || axis == AMOTION_EVENT_AXIS_Y ? value * g_scale : value;
}

// The part of the window not covered by system bars, where the game places its interface
static void scaledContentRectChanged(ANativeActivity* activity, const ARect* rect) {
    ARect scaledRect = {scaled(rect->left), scaled(rect->top), scaled(rect->right), scaled(rect->bottom)};
    game_onContentRectChanged(activity, &scaledRect);
}

// The scale picked in the instance settings, from AgentMainActivity.getResolutionScale().
// Anything outside (0, 1) means full resolution.
static float readScale(ANativeActivity* activity) {
    JNIEnv* env = activity->env;
    jclass activityClass = env->GetObjectClass(activity->clazz);
    jmethodID getter = env->GetMethodID(activityClass, "getResolutionScale", "()F");
    float scale = 1.0f;
    if (getter) {
        scale = env->CallFloatMethod(activity->clazz, getter);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    env->DeleteLocalRef(activityClass);
    return scale > 0.0f && scale < 1.0f ? scale : 1.0f;
}

// Replaces a function that libminecraftpe.so imports, starting from the next xhook_refresh
static void hook(const char* symbol, void* replacement, void** original) {
    if (xhook_register(GAME_LIBRARY, symbol, replacement, original) != 0) {
        LOGE("Failed to register the hook of %s", symbol);
    }
}

// Called from JNI_OnLoad, before its xhook_refresh. The scale isn't known yet at that point, so the
// hooks are set for every launch, and at full resolution they pass everything through unchanged.
void game_resolution_patch_register_hooks() {
    hook("ANativeWindow_setBuffersGeometry", reinterpret_cast<void*>(scaledSetBuffersGeometry),
         reinterpret_cast<void**>(&real_ANativeWindow_setBuffersGeometry));
    hook("AMotionEvent_getX", reinterpret_cast<void*>(scaledGetX),
         reinterpret_cast<void**>(&real_AMotionEvent_getX));
    hook("AMotionEvent_getY", reinterpret_cast<void*>(scaledGetY),
         reinterpret_cast<void**>(&real_AMotionEvent_getY));
    hook("AMotionEvent_getRawX", reinterpret_cast<void*>(scaledGetRawX),
         reinterpret_cast<void**>(&real_AMotionEvent_getRawX));
    hook("AMotionEvent_getRawY", reinterpret_cast<void*>(scaledGetRawY),
         reinterpret_cast<void**>(&real_AMotionEvent_getRawY));
    hook("AMotionEvent_getAxisValue", reinterpret_cast<void*>(scaledGetAxisValue),
         reinterpret_cast<void**>(&real_AMotionEvent_getAxisValue));
}

// Called before ANativeActivity_onCreate of the game, while the game has no window yet: takes the scale
// of this launch.
void game_resolution_patch_activity(ANativeActivity* activity) {
    g_scale = readScale(activity);
    LOGD("Resolution scale %f", g_scale);
}

// Called after ANativeActivity_onCreate of the game, which fills in activity->callbacks. Puts
// scaledContentRectChanged in front of the game's onContentRectChanged.
void game_resolution_patch_callbacks(ANativeActivity* activity) {
    if (g_scale == 1.0f || !activity->callbacks->onContentRectChanged) {
        return;
    }
    game_onContentRectChanged = activity->callbacks->onContentRectChanged;
    activity->callbacks->onContentRectChanged = scaledContentRectChanged;
}
