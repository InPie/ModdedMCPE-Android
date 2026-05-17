#ifndef ENDERHOOK_H
#define ENDERHOOK_H

#include <jni.h>

void enderhook_init(JavaVM* vm);

// Patches a JavaVM function table so AttachCurrentThread patches returned JNIEnv.
void enderhook_patch_vm(JavaVM* vm);

// Patches a JNIEnv function table so Environment.getExternalStorageDirectory(), returns custom java.io.File.
void enderhook_patch_env(JNIEnv* env);

void enderhook_set_external_storage_root(const char* path);


#endif // ENDERHOOK_H