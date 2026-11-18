#pragma once

#include <android/native_activity.h>
#include <jni.h>

// From JNI_OnLoad of libendercore.so
extern "C" void game_storage_patch_init(JavaVM* vm);

// Before ANativeActivity_onCreate of the game. The paths come from the activity's Context methods.
extern "C" void game_storage_patch_activity(ANativeActivity* activity, void* gameLibrary);
