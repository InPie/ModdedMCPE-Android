#ifndef ENDERHOOK_H
#define ENDERHOOK_H

#include <string>

/**
 * Initializes original libc function pointers and registers hooks via xhook.
 * Should be called in JNI_OnLoad.
 */
void enderhook_init_and_register();

/**
 * Sets the active instance data path for redirection.
 * Should be called when the path becomes available (e.g., in patchNativeActivity).
 */
void enderhook_set_data_path(const char* path);

#endif // ENDERHOOK_H
