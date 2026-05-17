#include "enderhook.h"
#include <string>
#include <dlfcn.h>
#include <cstdio>
#include <cstring>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdarg.h>
#include <dirent.h>
#include <utime.h>
#include <sys/time.h>
#include <sys/types.h>
#include "include/xhook.h"

static std::string g_instanceDataPath = "";
static std::string g_redirectionPath = ""; 
static size_t g_instanceDataPath_len = 0;

void enderhook_set_data_path(const char* path) {
    if (path) {
        g_instanceDataPath = path;
        g_instanceDataPath_len = g_instanceDataPath.length();
        g_redirectionPath = g_instanceDataPath + "/games/com.mojang";
    }
}

/**
 * Optimized path redirection.
 * Returns NULL if no redirection is needed, otherwise returns a pointer to a thread-local buffer.
 */
static thread_local char path_buffer[1024];

static const char* redirectPath(const char* pathname) {
    if (!pathname || g_instanceDataPath_len == 0) return NULL;

    // Fast check: assets usually don't start with / and don't start with games/
    if (pathname[0] != '/' && strncmp(pathname, "games/", 6) != 0) return NULL;

    // Double Redirection Check: If the path already contains our instance data path, skip.
    if (strncmp(pathname, g_instanceDataPath.c_str(), g_instanceDataPath_len) == 0) return NULL;

    // Search for the magic folder
    const char* mojang = strstr(pathname, "games/com.mojang");
    if (mojang) {
        // Skip the prefix "games/com.mojang" from the original path
        const char* subpath = mojang + 16; 
        
        size_t base_len = g_redirectionPath.length();
        size_t sub_len = strlen(subpath);
        
        if (base_len + sub_len + 1 > sizeof(path_buffer)) return NULL;

        memcpy(path_buffer, g_redirectionPath.c_str(), base_len);
        memcpy(path_buffer + base_len, subpath, sub_len + 1);
        
        return path_buffer;
    }
    return NULL;
}

// --- Function Pointers ---
static int (*old_open)(const char*, int, ...) = NULL;
static int (*old_openat)(int, const char*, int, ...) = NULL;
static int (*old_creat)(const char*, mode_t) = NULL;
static FILE* (*old_fopen)(const char*, const char*) = NULL;
static int (*old_mkdir)(const char*, mode_t) = NULL;
static int (*old_mkdirat)(int, const char*, mode_t) = NULL;
static int (*old_stat)(const char*, struct stat*) = NULL;
static int (*old_lstat)(const char*, struct stat*) = NULL;
static int (*old_fstatat)(int, const char*, struct stat*, int) = NULL;
static int (*old_access)(const char*, int) = NULL;
static int (*old_faccessat)(int, const char*, int, int) = NULL;
static int (*old_rename)(const char*, const char*) = NULL;
static int (*old_renameat)(int, const char*, int, const char*) = NULL;
static int (*old_remove)(const char*) = NULL;
static int (*old_unlink)(const char*) = NULL;
static int (*old_unlinkat)(int, const char*, int) = NULL;
static int (*old_rmdir)(const char*) = NULL;
static DIR* (*old_opendir)(const char*) = NULL;
static int (*old_truncate)(const char*, off_t) = NULL;
static int (*old_chmod)(const char*, mode_t) = NULL;
static int (*old_fchmodat)(int, const char*, mode_t, int) = NULL;
static int (*old_chown)(const char*, uid_t, gid_t) = NULL;
static int (*old_fchownat)(int, const char*, uid_t, gid_t, int) = NULL;
static int (*old_utime)(const char*, const struct utimbuf*) = NULL;
static int (*old_utimes)(const char*, const struct timeval[2]) = NULL;
static int (*old_utimensat)(int, const char*, const struct timespec[2], int) = NULL;

// --- Hook implementations ---

static int new_open(const char *pathname, int flags, ...) {
    const char* t = redirectPath(pathname);
    const char* p = t ? t : pathname;
    if (flags & O_CREAT) {
        va_list a; va_start(a, flags);
        mode_t m = static_cast<mode_t>(va_arg(a, int));
        va_end(a);
        return old_open ? old_open(p, flags, m) : -1;
    }
    return old_open ? old_open(p, flags) : -1;
}

static int new_openat(int dirfd, const char *pathname, int flags, ...) {
    const char* t = redirectPath(pathname);
    const char* p = t ? t : pathname;
    if (flags & O_CREAT) {
        va_list a; va_start(a, flags);
        mode_t m = static_cast<mode_t>(va_arg(a, int));
        va_end(a);
        return old_openat ? old_openat(dirfd, p, flags, m) : -1;
    }
    return old_openat ? old_openat(dirfd, p, flags) : -1;
}

static int new_creat(const char *pathname, mode_t mode) {
    return new_open(pathname, O_CREAT | O_WRONLY | O_TRUNC, mode);
}

static FILE* new_fopen(const char *pathname, const char *mode) {
    const char* t = redirectPath(pathname);
    return old_fopen ? old_fopen(t ? t : pathname, mode) : NULL;
}

static int new_mkdir(const char *pathname, mode_t mode) {
    const char* t = redirectPath(pathname);
    return old_mkdir ? old_mkdir(t ? t : pathname, mode) : -1;
}

static int new_mkdirat(int dirfd, const char *pathname, mode_t mode) {
    const char* t = redirectPath(pathname);
    return old_mkdirat ? old_mkdirat(dirfd, t ? t : pathname, mode) : -1;
}

static int new_stat(const char *pathname, struct stat *buf) {
    const char* t = redirectPath(pathname);
    return old_stat ? old_stat(t ? t : pathname, buf) : -1;
}

static int new_lstat(const char *pathname, struct stat *buf) {
    const char* t = redirectPath(pathname);
    return old_lstat ? old_lstat(t ? t : pathname, buf) : -1;
}

static int new_fstatat(int dirfd, const char *pathname, struct stat *buf, int flags) {
    const char* t = redirectPath(pathname);
    return old_fstatat ? old_fstatat(dirfd, t ? t : pathname, buf, flags) : -1;
}

static int new_access(const char *pathname, int mode) {
    const char* t = redirectPath(pathname);
    return old_access ? old_access(t ? t : pathname, mode) : -1;
}

static int new_faccessat(int dirfd, const char *pathname, int mode, int flags) {
    const char* t = redirectPath(pathname);
    return old_faccessat ? old_faccessat(dirfd, t ? t : pathname, mode, flags) : -1;
}

static int new_rename(const char *oldpath, const char *newpath) {
    const char* t1 = redirectPath(oldpath);
    std::string s1 = t1 ? t1 : oldpath; // Use string for first path to avoid buffer override
    const char* t2 = redirectPath(newpath);
    return old_rename ? old_rename(s1.c_str(), t2 ? t2 : newpath) : -1;
}

static int new_renameat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath) {
    const char* t1 = redirectPath(oldpath);
    std::string s1 = t1 ? t1 : oldpath;
    const char* t2 = redirectPath(newpath);
    return old_renameat ? old_renameat(olddirfd, s1.c_str(), newdirfd, t2 ? t2 : newpath) : -1;
}

static int new_remove(const char *pathname) {
    const char* t = redirectPath(pathname);
    return old_remove ? old_remove(t ? t : pathname) : -1;
}

static int new_unlink(const char *pathname) {
    const char* t = redirectPath(pathname);
    return old_unlink ? old_unlink(t ? t : pathname) : -1;
}

static int new_unlinkat(int dirfd, const char *pathname, int flags) {
    const char* t = redirectPath(pathname);
    return old_unlinkat ? old_unlinkat(dirfd, t ? t : pathname, flags) : -1;
}

static int new_rmdir(const char *pathname) {
    const char* t = redirectPath(pathname);
    return old_rmdir ? old_rmdir(t ? t : pathname) : -1;
}

static DIR* new_opendir(const char *name) {
    const char* t = redirectPath(name);
    return old_opendir ? old_opendir(t ? t : name) : NULL;
}

static int new_truncate(const char *path, off_t length) {
    const char* t = redirectPath(path);
    return old_truncate ? old_truncate(t ? t : path, length) : -1;
}

static int new_chmod(const char *path, mode_t mode) {
    const char* t = redirectPath(path);
    return old_chmod ? old_chmod(t ? t : path, mode) : -1;
}

static int new_fchmodat(int dirfd, const char *pathname, mode_t mode, int flags) {
    const char* t = redirectPath(pathname);
    return old_fchmodat ? old_fchmodat(dirfd, t ? t : pathname, mode, flags) : -1;
}

static int new_chown(const char *path, uid_t owner, gid_t group) {
    const char* t = redirectPath(path);
    return old_chown ? old_chown(t ? t : path, owner, group) : -1;
}

static int new_fchownat(int dirfd, const char *pathname, uid_t owner, gid_t group, int flags) {
    const char* t = redirectPath(pathname);
    return old_fchownat ? old_fchownat(dirfd, t ? t : pathname, owner, group, flags) : -1;
}

static int new_utime(const char *filename, const struct utimbuf *times) {
    const char* t = redirectPath(filename);
    return old_utime ? old_utime(t ? t : filename, times) : -1;
}

static int new_utimes(const char *filename, const struct timeval times[2]) {
    const char* t = redirectPath(filename);
    return old_utimes ? old_utimes(t ? t : filename, times) : -1;
}

static int new_utimensat(int dirfd, const char *pathname, const struct timespec times[2], int flags) {
    const char* t = redirectPath(pathname);
    return old_utimensat ? old_utimensat(dirfd, t ? t : pathname, times, flags) : -1;
}

void enderhook_init_and_register() {
    old_open = (int (*)(const char*, int, ...))dlsym(RTLD_NEXT, "open");
    old_openat = (int (*)(int, const char*, int, ...))dlsym(RTLD_NEXT, "openat");
    old_creat = (int (*)(const char*, mode_t))dlsym(RTLD_NEXT, "creat");
    old_fopen = (FILE* (*)(const char*, const char*))dlsym(RTLD_NEXT, "fopen");
    old_mkdir = (int (*)(const char*, mode_t))dlsym(RTLD_NEXT, "mkdir");
    old_mkdirat = (int (*)(int, const char*, mode_t))dlsym(RTLD_NEXT, "mkdirat");
    old_stat = (int (*)(const char*, struct stat*))dlsym(RTLD_NEXT, "stat");
    old_lstat = (int (*)(const char*, struct stat*))dlsym(RTLD_NEXT, "lstat");
    old_fstatat = (int (*)(int, const char*, struct stat*, int))dlsym(RTLD_NEXT, "fstatat");
    old_access = (int (*)(const char*, int))dlsym(RTLD_NEXT, "access");
    old_faccessat = (int (*)(int, const char*, int, int))dlsym(RTLD_NEXT, "faccessat");
    old_rename = (int (*)(const char*, const char*))dlsym(RTLD_NEXT, "rename");
    old_renameat = (int (*)(int, const char*, int, const char*))dlsym(RTLD_NEXT, "renameat");
    old_remove = (int (*)(const char*))dlsym(RTLD_NEXT, "remove");
    old_unlink = (int (*)(const char*))dlsym(RTLD_NEXT, "unlink");
    old_unlinkat = (int (*)(int, const char*, int))dlsym(RTLD_NEXT, "unlinkat");
    old_rmdir = (int (*)(const char*))dlsym(RTLD_NEXT, "rmdir");
    old_opendir = (DIR* (*)(const char*))dlsym(RTLD_NEXT, "opendir");
    old_truncate = (int (*)(const char*, off_t))dlsym(RTLD_NEXT, "truncate");
    old_chmod = (int (*)(const char*, mode_t))dlsym(RTLD_NEXT, "chmod");
    old_fchmodat = (int (*)(int, const char*, mode_t, int))dlsym(RTLD_NEXT, "fchmodat");
    old_chown = (int (*)(const char*, uid_t, gid_t))dlsym(RTLD_NEXT, "chown");
    old_fchownat = (int (*)(int, const char*, uid_t, gid_t, int))dlsym(RTLD_NEXT, "fchownat");
    old_utime = (int (*)(const char*, const struct utimbuf*))dlsym(RTLD_NEXT, "utime");
    old_utimes = (int (*)(const char*, const struct timeval[2]))dlsym(RTLD_NEXT, "utimes");
    old_utimensat = (int (*)(int, const char*, const struct timespec[2], int))dlsym(RTLD_NEXT, "utimensat");

    const char* lib = ".*libminecraftpe\\.so$";
    xhook_register(lib, "open", (void*)new_open, (void**)&old_open);
    xhook_register(lib, "openat", (void*)new_openat, (void**)&old_openat);
    xhook_register(lib, "creat", (void*)new_creat, (void**)&old_creat);
    xhook_register(lib, "fopen", (void*)new_fopen, (void**)&old_fopen);
    xhook_register(lib, "mkdir", (void*)new_mkdir, (void**)&old_mkdir);
    xhook_register(lib, "mkdirat", (void*)new_mkdirat, (void**)&old_mkdirat);
    xhook_register(lib, "stat", (void*)new_stat, (void**)&old_stat);
    xhook_register(lib, "lstat", (void*)new_lstat, (void**)&old_lstat);
    xhook_register(lib, "fstatat", (void*)new_fstatat, (void**)&old_fstatat);
    xhook_register(lib, "access", (void*)new_access, (void**)&old_access);
    xhook_register(lib, "faccessat", (void*)new_faccessat, (void**)&old_faccessat);
    xhook_register(lib, "rename", (void*)new_rename, (void**)&old_rename);
    xhook_register(lib, "renameat", (void*)new_renameat, (void**)&old_renameat);
    xhook_register(lib, "remove", (void*)new_remove, (void**)&old_remove);
    xhook_register(lib, "unlink", (void*)new_unlink, (void**)&old_unlink);
    xhook_register(lib, "unlinkat", (void*)new_unlinkat, (void**)&old_unlinkat);
    xhook_register(lib, "rmdir", (void*)new_rmdir, (void**)&old_rmdir);
    xhook_register(lib, "opendir", (void*)new_opendir, (void**)&old_opendir);
    xhook_register(lib, "truncate", (void*)new_truncate, (void**)&old_truncate);
    xhook_register(lib, "chmod", (void*)new_chmod, (void**)&old_chmod);
    xhook_register(lib, "fchmodat", (void*)new_fchmodat, (void**)&old_fchmodat);
    xhook_register(lib, "chown", (void*)new_chown, (void**)&old_chown);
    xhook_register(lib, "fchownat", (void*)new_fchownat, (void**)&old_fchownat);
    xhook_register(lib, "utime", (void*)new_utime, (void**)&old_utime);
    xhook_register(lib, "utimes", (void*)new_utimes, (void**)&old_utimes);
    xhook_register(lib, "utimensat", (void*)new_utimensat, (void**)&old_utimensat);
}
