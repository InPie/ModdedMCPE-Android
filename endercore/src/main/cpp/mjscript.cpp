/**
 *  Scripting Language Library
 *
 *  Gratefulness:
 *   非常感谢 zhuowei (GitHub: zhuowei)！此方法来源于他的 BlockLauncher
 *   (GitHub: zhuowei/MCPELauncher)。事实上，zhuowei 是第一个在安卓设备上解锁脚本引擎的人。
 *   应 MiemieMethod (GitHub: MiemieMethod) 提议，现将其在最新版本中实现。
 *   由于 CydiaSubstrate 在 arm64-v8a 上不可用，此处使用的是 xHook (GitHub: iqiyi/xHook)。
 *  Китай кошка жена миска риса
 *
 */

#include <jni.h>
#include <dlfcn.h>
#include <elf.h>
#include <android/log.h>
#include <string>
#include "include/dobby/dobby.h"
#include "include/xhook.h"
#include "include/yurai/statichook.h"

#define LOG_TAG "EnderCore-mjscript"
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))

JavaVM* sl_JavaVM;
void *mcpe_handle = nullptr;


#ifdef __LP64__
    typedef Elf64_Sym Elf_Sym;
#else
    typedef Elf32_Sym Elf_Sym;
#endif



// =============== BASIC FUNCTIONAL ===============

void showToast( JNIEnv* env, jobject activity, const char* message ) {
    jstring jMessage = env->NewStringUTF(message);

    jclass toastClass = env->FindClass("android/widget/Toast");
    jmethodID makeText = env->GetStaticMethodID(toastClass, "makeText", "(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;");
    jmethodID show = env->GetMethodID(toastClass, "show", "()V");

    jobject toast = env->CallStaticObjectMethod(toastClass, makeText, activity, jMessage, 0);
    env->CallVoidMethod(toast, show);

    env->DeleteLocalRef(toastClass);
    env->DeleteLocalRef(jMessage);
    env->DeleteLocalRef(toast);
}

void sl_dumpVtable(void** vtable, size_t size) {
    Dl_info info;
    for (int i = 0; i < (size / sizeof(void*)); i++) {
        if (!dladdr(vtable[i], &info)) continue;
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Dump vtable  %d: %s", i, info.dli_sname);
    }
}
int sl_findVtable(void** vtable, void* needle) {
    int i = 0;
    while (vtable[i] != needle) i++;
    return i;
}

int sl_vtableIndex(void* si, const char* vtablename, const char* name) {
    void* needle = dobby_dlsym(si, name);
    Elf_Sym* vtableSym = dobby_elfsym(si, vtablename);
    void** vtable = static_cast<void **>(dobby_dlsym(si, vtablename));
    for (int i = 0; i < (vtableSym->st_size / sizeof(void*)); i++) {
        if (vtable[i] == needle) {
            return i;
        }

    }
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Cannot find vtable entry %s in %s", name, vtablename);
    return 0;
}



// =============== BASIC APP HOOK FUNCTIONS ===============

static bool (*realAppPlatform_supportsScripting)( void* );
static bool newAppPlatform_supportsScripting( void *_this )
{
    bool original = realAppPlatform_supportsScripting( _this );
    LOGI( "HOOK AppPlatform::supportsScripting( THIS ) : %s",
          original ? "true" : "false" );
    return original;
}

static bool (*realAppPlatform_android_supportsScripting)( void* );
static bool newAppPlatform_android_supportsScripting( void *_this )
{
    bool original = realAppPlatform_android_supportsScripting( _this );
    LOGI( "HOOK AppPlatform_android::supportsScripting( THIS ) : %s -> true",
          original ? "true" : "false" );
    return true;
}

static bool (*realClientInstance_isScriptingEnabled)( void* );
static bool newClientInstance_isScriptingEnabled( void *_this )
{
    bool original = realClientInstance_isScriptingEnabled( _this );
    LOGI( "HOOK ClientInstance::isScriptingEnabled( THIS ) : %s",
          original ? "true" : "false" );
    return original;
}

static bool (*realExperiments_Scripting)( void* );
static bool newExperiments_Scripting( void *_this )
{
    bool original = realExperiments_Scripting( _this );
    LOGI( "HOOK Experiments::Scripting( THIS ) : %s",
          original ? "true" : "false" );
    return original;
}

static bool (*realFeatureToggles_isEnabled)( void*, int );
static bool newFeatureToggles_isEnabled( void *_this, int id )
{
    bool original = realFeatureToggles_isEnabled( _this, id );
    LOGI( "HOOK FeatureToggles::isEnabled( THIS, %d ) : %s -> true",
          id, original ? "true" : "false" );
    return true;
}

static bool (*realScriptEngine_isScriptingEnabled)( void* );
static bool newScriptEngine_isScriptingEnabled( void *_this )
{
    bool original = realScriptEngine_isScriptingEnabled( _this );
    LOGI( "HOOK ScriptEngine::isScriptingEnabled( THIS ) : %s -> true",
          original ? "true" : "false" );
    return true;
}

static bool (*realhbui_Feature_isEnabled)( void* );
static bool newhbui_Feature_isEnabled( void *_this )
{
    bool original = realhbui_Feature_isEnabled( _this );
    LOGI( "HOOK hbui::Feature::isEnabled( THIS ) : %s -> true",
          original ? "true" : "false" );
    return true;
}



// =============== VTABLE Indexes, TYPES, VARS & SYMBOLS ===============

// vtable indexes
struct vtable_indexes_t {
    int minecraft_update;
    int minecraft_quit;
    int gamemode_tick;
    int gamemode_use_item_on;
    int gamemode_attack;
    int gamemode_start_destroy_block;
    int entity_get_entity_type_id;
};

static struct vtable_indexes_t vtable_indexes;

// types
#define FALSE 0
#define TRUE 1

#define AXIS_X 0
#define AXIS_Y 1
#define AXIS_Z 2

#define ITEMID 0
#define DAMAGE 1
#define AMOUNT 2

struct unique_ptr {
    void* ptr;
};

typedef void Minecraft;

struct Vec3 {
    float x;
    float y;
    float z;
};

struct Vec2 {
    float x;
    float y;
};

struct MCPETimer {
    float ticksPerSecond; //0
    int elapsedTicks; //4
    float renderPartialTicks; //8
    float timerSpeed; //12
    float elapsedPartialTicks; //16

};

struct TileSource;

struct EntityUniqueID {
    long long id;
};

struct Entity {
    void** vtable{}; //0
    int filler3[5]{};//4
    float x{}; //24
    float y{}; //28
    float z{}; //32
    char filler2[48-36]{}; //36
    TileSource* tileSource{}; // 48
    int dimension{}; // 52
    char filler2_[76-56]{}; // 56
    float motionX{}; //76 found in Entity::rideTick(); should be set to 0 there
    float motionY{}; //80
    float motionZ{}; //84
    float yaw{}; //88
    float pitch{}; //92
    float prevYaw{}; //96
    float prevPitch{}; //100
    char filler4[268-104]{}; //104
    int renderType{}; //268
    char filler5[284-272]{}; // 272
    struct Entity* rider{}; //284
    struct Entity* riding{}; //288
    char filler6[320-292]{}; //292

    #ifdef __cplusplus
        EntityUniqueID entityId{}; // 320
    #else
        long long entityId; // 320
    #endif
};

class Level {
public:
    void** vtable;     // 0
    char filler[12-4]; // 8 bytes
    bool isRemote;     // 12 PrimedTnT::normalTick
    char filler2[2908-13]; // 2908 - 13 = 2895 bytes

    Entity* getEntity(EntityUniqueID, bool) const;
    void addEntity(std::unique_ptr<Entity>);
};

typedef Entity Player;

// vars
Minecraft* sl_minecraft;
Level* sl_level;
Player* sl_localplayer;
void* sl_gamemode;

// symbols
static Player* (*sl_MinecraftClient_getPlayer)(Minecraft*);
static Level* (*sl_Minecraft_getLevel)(Minecraft*);
static void (*sl_LocalPlayer_setSneaking)(Player*, bool);



// =============== MINECRAFT GAME HOOK FUNCTIONS ===============

static void (*sl_GameMode_tick_real)( void* gamemode );
static void sl_GameMode_tick_hook( void* gamemode ) {
    sl_level = sl_Minecraft_getLevel(sl_minecraft);
    sl_localplayer = sl_MinecraftClient_getPlayer(sl_minecraft);
    sl_gamemode = gamemode;

    sl_GameMode_tick_real(gamemode);
}

static void (*sl_MinecraftClient_onClientStartedLevel_real)( Minecraft* minecraft, unique_ptr* levelPtr );
static void sl_MinecraftClient_onClientStartedLevel_hook( Minecraft* minecraft, unique_ptr* levelPtr ) {
    sl_minecraft = minecraft;
    sl_level = static_cast<Level *>(levelPtr->ptr);

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Start level called: %s", (char*) (sl_level != NULL));
    sl_MinecraftClient_onClientStartedLevel_real(minecraft, levelPtr);
}

static void* (*sl_MinecraftClient_startLocalServer_real)( Minecraft* minecraft, void* wDir, void* wName, void* levelSettings );
static void* sl_MinecraftClient_startLocalServer_hook( Minecraft* minecraft, void* wDir, void* wName, void* levelSettings ) {
    sl_minecraft = minecraft;
    sl_level = sl_Minecraft_getLevel(minecraft);
    sl_localplayer = sl_MinecraftClient_getPlayer(minecraft);

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Local server started: %s", (char*) (wName != NULL));
    void* returnValue = sl_MinecraftClient_startLocalServer_real(minecraft, wDir, wName, levelSettings);
    return returnValue;
}

static void (*sl_Minecraft_leaveGame_real)( Minecraft* minecraft, int state );
static void sl_Minecraft_leaveGame_hook( Minecraft* minecraft, int state ) {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Leave game called");
    sl_Minecraft_leaveGame_real(minecraft, state);
}



// =============== ON LOADING THIS LIB ===============

static void populate_vtable_indexes() {
    vtable_indexes.minecraft_update = sl_vtableIndex( mcpe_handle, "_ZTV9Minecraft", "_ZN9Minecraft6updateEv");
    vtable_indexes.minecraft_quit = sl_vtableIndex( mcpe_handle, "_ZTV15MinecraftClient", "_ZN3App4quitEv");
    vtable_indexes.gamemode_tick = sl_vtableIndex( mcpe_handle, "_ZTV8GameMode", "_ZN8GameMode4tickEv");
    vtable_indexes.gamemode_use_item_on = sl_vtableIndex( mcpe_handle, "_ZTV8GameMode", "_ZN8GameMode9useItemOnER6PlayerP12ItemInstanceRK7TilePosaRK4Vec3");
    vtable_indexes.gamemode_attack = sl_vtableIndex( mcpe_handle, "_ZTV8GameMode", "_ZN8GameMode6attackEP6PlayerP6Entity");
    vtable_indexes.gamemode_start_destroy_block = sl_vtableIndex( mcpe_handle, "_ZTV8GameMode", "_ZN8GameMode17startDestroyBlockEP6Playeriiia");
    vtable_indexes.entity_get_entity_type_id = sl_vtableIndex( mcpe_handle, "_ZTV3Pig", "_ZNK3Pig15getEntityTypeIdEv") - 2;
}

void init_symbols() {
    void** minecraftVtable = (void**) dobby_dlsym(mcpe_handle, "_ZTV15MinecraftClient");

    //sl_NinecraftApp_update_real = minecraftVtable[vtable_indexes.minecraft_update];
    //minecraftVtable[vtable_indexes.minecraft_update] = &sl_NinecraftApp_update_hook;

    //App_quit_real = minecraftVtable[vtable_indexes.minecraft_quit];
    //minecraftVtable[vtable_indexes.minecraft_quit] = &App_quit_hook;

    void** creativeVtable = (void**) dobby_dlsym(mcpe_handle, "_ZTV12CreativeMode");
    void** survivalVtable = (void**) dobby_dlsym(mcpe_handle, "_ZTV12SurvivalMode");

    sl_GameMode_tick_real = (void (*)(void*)) dlsym(mcpe_handle, "_ZN8GameMode4tickEv");
    creativeVtable[vtable_indexes.gamemode_tick] = (void*) &sl_GameMode_tick_hook;
    survivalVtable[vtable_indexes.gamemode_tick] = (void*) &sl_GameMode_tick_hook;

    //creativeVtable[vtable_indexes.gamemode_use_item_on] = (void*) &sl_GameMode_useItemOn_hook;
    //survivalVtable[vtable_indexes.gamemode_use_item_on] = (void*) &sl_GameMode_useItemOn_hook;

    //sl_GameMode_attack_real = dlsym(mcpe_handle, "_ZN8GameMode6attackEP6PlayerP6Entity");
    //creativeVtable[vtable_indexes.gamemode_attack] = (void*) &sl_GameMode_attack_hook;
    //survivalVtable[vtable_indexes.gamemode_attack] = (void*) &sl_GameMode_attack_hook;

    //sl_CreativeMode_startDestroyBlock_real = creativeVtable[vtable_indexes.gamemode_start_destroy_block];
    //creativeVtable[vtable_indexes.gamemode_start_destroy_block] = &sl_CreativeMode_startDestroyBlock_hook;
    //sl_SurvivalMode_startDestroyBlock_real = survivalVtable[vtable_indexes.gamemode_start_destroy_block];
    //survivalVtable[vtable_indexes.gamemode_start_destroy_block] = &sl_SurvivalMode_startDestroyBlock_hook;

    int minecraftVtableOnClientStartedLevel = sl_vtableIndex(mcpe_handle, "_ZTV15MinecraftClient", "_ZN15MinecraftClient20onClientStartedLevelESt10unique_ptrI5LevelSt14default_deleteIS1_EES0_I11LocalPlayerS2_IS5_EE");
    sl_MinecraftClient_onClientStartedLevel_real = (void (*)(Minecraft*, unique_ptr*)) minecraftVtable[minecraftVtableOnClientStartedLevel];
    minecraftVtable[minecraftVtableOnClientStartedLevel] = (void*) &sl_MinecraftClient_onClientStartedLevel_hook;



    sl_MinecraftClient_getPlayer = (Player* (*)(Minecraft*)) dlsym(mcpe_handle, "_ZN15MinecraftClient9getPlayerEv");
    sl_Minecraft_getLevel = (Level* (*)(Minecraft*)) dlsym(mcpe_handle, "_ZN9Minecraft8getLevelEv");
    sl_LocalPlayer_setSneaking = (void (*)(Player*, bool)) dlsym(mcpe_handle, "_ZN11LocalPlayer11setSneakingEb");
}

JNIEXPORT jint JNI_OnLoad( JavaVM *vm, void *reserved )
{
    sl_JavaVM = vm;
    mcpe_handle = dlopen("libminecraftpe.so", RTLD_LAZY);

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "mcpe_handle = %p", mcpe_handle);
    if (!mcpe_handle) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "dlopen() failed: %s", dlerror());
    }

    xhook_enable_debug( 1 );
                    // Match WHOLE LINE that ends with "libminecraftpe.so"
    xhook_register( ".*libminecraftpe\\.so$",
                    "_ZNK11AppPlatform17supportsScriptingEv",
                    (void* ) &newAppPlatform_supportsScripting,
                    (void**) &realAppPlatform_supportsScripting );
    xhook_register( ".*libminecraftpe\\.so$",
                    "_ZNK19AppPlatform_android17supportsScriptingEv",
                    (void* ) &newAppPlatform_android_supportsScripting,
                    (void**) &realAppPlatform_android_supportsScripting );
    xhook_register( ".*libminecraftpe\\.so$",
                    "_ZNK14ClientInstance18isScriptingEnabledEv",
                    (void* ) &newClientInstance_isScriptingEnabled,
                    (void**) &realClientInstance_isScriptingEnabled );
    xhook_register( ".*libminecraftpe\\.so$",
                    "_ZNK11Experiments9ScriptingEv",
                    (void* ) &newExperiments_Scripting,
                    (void**) &realExperiments_Scripting );
    xhook_register( ".*libminecraftpe\\.so$",
                    "_ZNK14FeatureToggles9isEnabledE15FeatureOptionID",
                    (void* ) &newFeatureToggles_isEnabled,
                    (void**) &realFeatureToggles_isEnabled );
    xhook_register( ".*libminecraftpe\\.so$",
                    "_ZN12ScriptEngine18isScriptingEnabledEv",
                    (void* ) &newScriptEngine_isScriptingEnabled,
                    (void**) &realScriptEngine_isScriptingEnabled );
    xhook_register( ".*libminecraftpe\\.so$",
                    "_ZNK4hbui7Feature9isEnabledEv",
                    (void* ) &newhbui_Feature_isEnabled,
                    (void**) &realhbui_Feature_isEnabled );

    // Define script language hooks

    //TODO: Fix critical errors and make it work (!!)
    populate_vtable_indexes();
    init_symbols();
    xhook_register( ".*libminecraftpe\\.so$",
                    "_ZN15MinecraftClient16startLocalServerESsSs13LevelSettings",
                    (void* ) &sl_MinecraftClient_startLocalServer_hook,
                    (void**) &sl_MinecraftClient_startLocalServer_real );
    xhook_register( ".*libminecraftpe\\.so$",
                    "_ZN9Minecraft9leaveGameEb",
                    (void* ) &sl_Minecraft_leaveGame_hook,
                    (void**) &sl_Minecraft_leaveGame_real );

    xhook_refresh( 1 );

    return JNI_VERSION_1_6;
}



// =============== SCRIPT LANGUAGE JNI FUNCTIONS ===============

extern "C" JNIEXPORT void JNICALL
Java_org_endercore_android_mod_script_ScriptController_executeCustomFunction(JNIEnv* env, jclass clazz, jobject activity) {
    __android_log_print(ANDROID_LOG_INFO, "MJScript", "Custom JNI function executed!");
    showToast(env, activity, "Custom JNI function executed!");
}

extern "C" JNIEXPORT void JNICALL
Java_org_endercore_android_mod_script_ScriptController_shiftPlayer(JNIEnv* env, jclass clazz, jboolean state) {
    void** vtableLocalPlayer = (void**) dlsym(mcpe_handle, "_ZTV11LocalPlayer");
    uintptr_t vtable_offset = ((uintptr_t)sl_LocalPlayer_setSneaking - (uintptr_t)vtableLocalPlayer) / sizeof(void*);

    void** vtable = *(void***) sl_localplayer;
    auto _setSneaking = (void (*)(Player*, bool)) vtable[vtable_offset];
    _setSneaking(sl_localplayer, state);

    __android_log_print(ANDROID_LOG_INFO, "MJScript", "Shift Player executed: %d", state);
}
