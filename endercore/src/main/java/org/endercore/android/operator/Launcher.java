package org.endercore.android.operator;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.endercore.android.EnderCore;
import org.endercore.android.exception.LauncherException;
import org.endercore.android.exception.NModException;
import org.endercore.android.interf.IFileEnvironment;
import org.endercore.android.interf.IInitializationListener;
import org.endercore.android.interf.implemented.InitializationListener;
import org.endercore.android.mod.nmod.NMod;
import org.endercore.android.mod.nmod.overrider.FileOverrider;
import org.endercore.android.mod.nmod.overrider.JsonOverrider;
import org.endercore.android.mod.nmod.overrider.TextOverrider;
import org.endercore.android.utils.CPUArch;
import org.endercore.android.utils.FileUtils;
import org.endercore.android.utils.NModJsonBean;
import org.endercore.android.utils.Patcher;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;
//0.8 comp
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public final class Launcher {
    private final EnderCore core;
    private IInitializationListener listener;
    private final ArrayList<String> patchAssetPath;
    private final ArrayList<String> patchDexPath;
    private final ArrayList<String> patchLibPath;
    private boolean initializedGame;

    private final static String ASSETS_MAIN_DIR = "endercore" + File.separator + "android";
    private final static String ASSETS_FILE_AGENT_DEX = ASSETS_MAIN_DIR + File.separator + "AgentMainActivity.dex";
    private final static String ASSETS_FILE_CRACKER_DEX = ASSETS_MAIN_DIR + File.separator + "CrackedLicense.dex";
    private final static String NAME_AGENT_DEX = "AgentMainActivity.dex";
    private final static String NAME_CRACKER_DEX = "CrackedLicense.dex";
    private final static String NAME_CPP_SHARED = "libc++_shared.so";
    private final static String NAME_YURAI = "libyurai.so";
    private final static String NAME_SUBSTRATE = "libsubstrate.so";
    private final static String NAME_XHOOK = "libxhook.so";
    private final static String NAME_FMOD = "libfmod.so";
    private final static String NAME_GNUSTL_SHARED = "libgnustl_shared.so";
    private final static String NAME_MINECRAFTPE = "libminecraftpe.so";
    private final static String NAME_ENDERCORE = "libendercore.so";
    private final static String NAME_MJSCRIPT = "libmjscript.so";
    private final static String LIB_CPP_SHARED = "c++_shared";
    private final static String LIB_YURAI = "yurai";
    private final static String LIB_SUBSTRATE = "substrate";
    private final static String LIB_XHOOK = "xhook";
    private final static String LIB_FMOD = "fmod";
    private final static String LIB_GNUSTL_SHARED = "gnustl_shared";
    private final static String LIB_MINECRAFTPE = "minecraftpe";
    private final static String LIB_ENDERCORE = "endercore";
    private final static String LIB_MJSCRIPT = "mjscript";
    private final static String DIR_LIB = "lib";
    private final static String CLASS_MAIN_ACTIVITY = "com.mojang.minecraftpe.MainActivity";

    public Launcher(EnderCore core) {
        initializedGame = false;
        patchAssetPath = new ArrayList<>();
        patchDexPath = new ArrayList<>();
        patchLibPath = new ArrayList<>();
        this.core = core;
        listener = new InitializationListener();
    }

    public ArrayList<NModException> initializeGame(Context context) throws LauncherException {
        listener.onStart();
        ArrayList<NModException> nModExceptions = null;
        try {
            Log.d("EnderCore-Launcher", "Initialization of the game...");

            // Check Availability
            if (!core.getGamePackageManager().isGameInstalled())
                throw new LauncherException("Minecraft Game is not installed. Please install game.");

            // Set Variants
            IFileEnvironment fileEnvironment = core.getFileEnvironment();
            NModManager nModManager = core.getNModManager();
            OptionsManager optionsManager = core.getOptionsManager();
            String targetArch = null;
            boolean[] dexExists = new boolean[10];
            for (int i = 0; i < 9; ++i)
                dexExists[i] = false;

            listener.onLoadGameFilesStart();

            // Copy Game Files
            try {
                File resPath = new File(core.getGamePackageManager().getPackageResourcePath());
                if (resPath.getParentFile() == null)
                    throw new IOException("Invalid file path.");
                File[] allApkFiles = resPath.getParentFile().listFiles();
                if (allApkFiles == null)
                    throw new IOException("Failed to list all apk files.");
                //Copy native libraries
                String[] supportedAbis = CPUArch.getSystemSupportedAbis();
                String[] requiredLibs = {NAME_FMOD, NAME_GNUSTL_SHARED, NAME_MINECRAFTPE};
                boolean[] libsCopied = new boolean[requiredLibs.length];
                for (int i = 0; i < requiredLibs.length; ++i)
                    libsCopied[i] = false;

                // find target arch
                for (String abiItem : supportedAbis) {
                    for (File apkFile : allApkFiles) {
                        if (!apkFile.isFile())
                            continue;

                        try {
                            ZipFile zipFileOfApk;
                            zipFileOfApk = new ZipFile(apkFile);
                            Enumeration<? extends ZipEntry> enumeration = zipFileOfApk.entries();
                            while (enumeration.hasMoreElements()) {
                                ZipEntry zipEntry = enumeration.nextElement();
                                if (zipEntry.getName().startsWith(DIR_LIB + File.separator + abiItem) && CPUArch.isEnderCoreSupportedAbi(abiItem))
                                    targetArch = abiItem;
                            }
                        } catch (IOException ignored) {
                        }

                    }
                    if (targetArch != null)
                        break;
                }
                if (targetArch == null)
                    throw new LauncherException("Abis are not supported by EnderCore.");

                Log.d("EnderCore-Launcher", "Selected architecture: " + targetArch);

                // copy game native libraries
                for (int i = 0; i < requiredLibs.length; ++i) {
                    String libName = requiredLibs[i];
                    if (!libsCopied[i]) {
                        for (File apk : allApkFiles) {
                            if (!apk.isFile())
                                continue;
                            ZipEntry targetEntry;
                            ZipFile apkFile;
                            try {
                                apkFile = new ZipFile(apk);
                                targetEntry = apkFile.getEntry(DIR_LIB + File.separator + targetArch + File.separator + libName);
                            } catch (IOException ignored) {
                                continue;
                            }

                            if (targetEntry != null) {
                                listener.onCopyGameFile(libName);
                                FileUtils.copy(apkFile.getInputStream(targetEntry), new File(fileEnvironment.getCodeCacheDirPathForNativeLib(), libName));
                                libsCopied[i] = true;
                            }
                        }
                    }
                }
                boolean allLibsCopied = true;
                int notCopiedLibId = -1;
                for (int i = 0; i < libsCopied.length; ++i) {
                    boolean copied = libsCopied[i];
                    if (!copied) {
                        allLibsCopied = false;
                        notCopiedLibId = i;
                        break;
                    }
                }
                //if (!allLibsCopied)
                //    throw new LauncherException("Not all required libs are found int the minecraft game package. Lib " + requiredLibs[notCopiedLibId] + " of arch " + targetArch + " not found.");

                //Copy Dex files
                for (int i = 9; i >= 0; --i) {
                    String libName = "classes" + (i == 0 ? "" : i) + ".dex";
                    for (File apk : allApkFiles) {
                        if (!apk.isFile())
                            continue;

                        ZipEntry targetEntry;
                        ZipFile apkFile;
                        try {
                            apkFile = new ZipFile(apk);
                            targetEntry = apkFile.getEntry(libName);
                        } catch (IOException e) {
                            continue;
                        }

                        if (targetEntry != null) {
                            listener.onCopyGameFile(libName);
                            FileUtils.copy(apkFile.getInputStream(targetEntry), new File(fileEnvironment.getCodeCacheDirPathForDex(), libName));
                            dexExists[i] = true;
                        }
                    }
                }
            } catch (IOException ioexception) {
                throw new LauncherException("Extract game libraries failed.", ioexception);
            }
            Log.d("EnderCore-Launcher", "Game Files Copied");

            listener.onLoadJavaLibrariesStart();
            try {
                for (int i = 9; i >= 0; --i) {
                    String dexLibName = "classes" + (i == 0 ? "" : i) + ".dex";
                    File path = new File(fileEnvironment.getCodeCacheDirPathForDex(), dexLibName);
                    if (dexExists[i]) {
                        listener.onLoadJavaLibrary(dexLibName);
                        if (!path.setReadOnly()) { // Android 15 fix
                            throw new LauncherException("Unable to set file to read-only: " + path.getAbsolutePath());
                        }
                        Patcher.patchDexFile(context.getClassLoader(), path.getAbsolutePath(), fileEnvironment.getCodeCacheDirPathForDexOpt());
                        patchDexPath.add(path.getAbsolutePath());
                        path.setWritable(true);
                    }
                }

                if (optionsManager.getAutoLicense()) {
                    //Crack License Checker
                    File licenseCrackerDex = new File(fileEnvironment.getCodeCacheDirPathForDex(), NAME_CRACKER_DEX);
                    FileUtils.copy(context.getAssets().open(ASSETS_FILE_CRACKER_DEX), licenseCrackerDex);
                    if (!licenseCrackerDex.setReadOnly()) { // Android 15 fix
                        throw new LauncherException("Unable to set file to read-only: " + licenseCrackerDex.getAbsolutePath());
                    }
                    listener.onLoadJavaLibrary(NAME_CRACKER_DEX);
                    Patcher.patchDexFile(context.getClassLoader(), licenseCrackerDex.getAbsolutePath(), fileEnvironment.getCodeCacheDirPathForDexOpt());
                    patchDexPath.add(licenseCrackerDex.getAbsolutePath());
                    licenseCrackerDex.setWritable(true);
                }
            } catch (IllegalAccessException | IOException | NoSuchFieldException e) {
                throw new LauncherException("Exception occurred while loading *.dex file.", e);
            }
            listener.onLoadJavaLibrariesFinish();
            Log.d("EnderCore-Launcher", "Dex File Loaded.");

            // Load Native Libs
            listener.onLoadNativeLibrariesStart();
            try {
                Patcher.patchNativeLibraryDir(context.getClassLoader(), fileEnvironment.getCodeCacheDirPathForNativeLib());
                patchLibPath.add(fileEnvironment.getCodeCacheDirPathForNativeLib());
            } catch (IllegalAccessException | ClassNotFoundException | NoSuchFieldException | NoSuchMethodException | InvocationTargetException | InstantiationException e) {
                throw new LauncherException("Exception occurred while loading *.so file.", e);
            }
            try {
                File nativeLibDir = new File(fileEnvironment.getCodeCacheDirPathForNativeLib());

                if (new File(nativeLibDir, NAME_CPP_SHARED).exists()) {
                    listener.onLoadNativeLibrary(NAME_CPP_SHARED);
                    System.loadLibrary(LIB_CPP_SHARED);
                } else {
                    Log.e("EnderCore-Launcher", "Native library " + NAME_CPP_SHARED + " not found in " + nativeLibDir.getAbsolutePath());
                }

                if (new File(nativeLibDir, NAME_FMOD).exists()) {
                    listener.onLoadNativeLibrary(NAME_FMOD);
                    System.loadLibrary(LIB_FMOD);
                } else {
                    Log.e("EnderCore-Launcher", "Native library " + NAME_FMOD + " not found in " + nativeLibDir.getAbsolutePath());
                }

                if (new File(nativeLibDir, NAME_GNUSTL_SHARED).exists()) {
                    listener.onLoadNativeLibrary(NAME_GNUSTL_SHARED);
                    System.loadLibrary(LIB_GNUSTL_SHARED);
                } else {
                    Log.e("EnderCore-Launcher", "Native library " + NAME_GNUSTL_SHARED + " not found in " + nativeLibDir.getAbsolutePath());
                }

                loadMinecraftNativeLibrary(context, core.getGamePackageManager().getVersionName());

                listener.onLoadNativeLibrary(NAME_YURAI);
                System.loadLibrary(LIB_YURAI);
                listener.onLoadNativeLibrary(NAME_SUBSTRATE);
                System.loadLibrary(LIB_SUBSTRATE);
                listener.onLoadNativeLibrary(NAME_XHOOK);
                System.loadLibrary(LIB_XHOOK);
                listener.onLoadNativeLibrary(NAME_ENDERCORE);
                System.loadLibrary(LIB_ENDERCORE);

                if (optionsManager.getUnlockMjscript()) {
                    listener.onLoadNativeLibrary(NAME_MJSCRIPT);
                    System.loadLibrary(LIB_MJSCRIPT);
                }
            } catch (Error | ClassNotFoundException error) {
                throw new LauncherException("Load game libraries failed.", error);
            }
            listener.onLoadNativeLibrariesFinish();

            Log.d("EnderCore-Launcher", "Native Libs Loaded.");

            // Load Resources
            listener.onLoadResourcesStart();
            patchAssetPath.add(context.getPackageResourcePath());
            String basePath = core.getGamePackageManager().getPackageResourcePath();
            patchAssetPath.add(basePath);
            /* In `1.17.30`(beta version unknown), almost all assets files were moved to
             * `split_install_pack.apk`, including `bootstrap.json`, a file that is crucial to
             * launching the game.
             */
            String splitPath = basePath.replace("base.apk", "split_install_pack.apk");
            File splitFile = new File(splitPath);
            if (splitFile.exists()) {
                patchAssetPath.add(splitPath);
            }
            listener.onLoadResourcesFinish();

            listener.onLoadGameFilesFinish();

            // Load NMods
            if (optionsManager.getUseNMods()) {
                listener.onLoadNModsStart();
                nModExceptions = new ArrayList<>();

                // Extract all assets from game apk
                File assetsDir = new File(fileEnvironment.getCodeCacheDirPathForAssets());
                boolean mkdirsResult = assetsDir.mkdirs();
                if (!mkdirsResult)
                    throw new LauncherException("Failed to mkdirs: " + assetsDir.getAbsolutePath() + ".");

                File resPath = new File(core.getGamePackageManager().getPackageResourcePath());
                if (resPath.getParentFile() == null)
                    throw new IOException("Invalid file path.");
                File[] allApkFiles = resPath.getParentFile().listFiles();
                if (allApkFiles == null)
                    throw new IOException("Failed to list all apk files.");
                for (File apkFile : allApkFiles) {
                    try {
                        ZipFile zipFile = new ZipFile(apkFile);
                        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
                        while (enumeration.hasMoreElements()) {
                            ZipEntry entry = enumeration.nextElement();
                            if (entry.getName().startsWith("assets/")) {
                                FileUtils.copy(zipFile.getInputStream(entry), new File(assetsDir, entry.getName()));
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }

                ArrayList<NMod> enabledNMods = nModManager.getEnabledNMods();
                for (NMod nmod : enabledNMods) {
                    listener.onLoadNMod(nmod);
                    try {
                        NModJsonBean.GameSupportData gameSupport = null;
                        // Find a proper game support
                        for (NModJsonBean.GameSupportData gameSupportData : nmod.getPackageManifest().game_supports) {
                            for (String version : gameSupportData.target_game_versions) {
                                if (Pattern.matches(version, core.getGamePackageManager().getVersionName())) {
                                    gameSupport = gameSupportData;
                                }
                            }
                        }

                        if (gameSupport == null) {
                            throw new NModException("Cannot find a proper game version support for NMod " + nmod.getUUID() + ".");
                        }

                        // Patch assets for NMods
                        for (NModJsonBean.FileOverrideData fileOverrideData : gameSupport.file_overrides) {
                            listener.onLoadNModAsset(fileOverrideData.path);
                            FileOverrider overrider = new FileOverrider(new File(assetsDir, "assets"));
                            overrider.performOverride(new File(nmod.getGameSupportDir(gameSupport.name), "assets"), fileOverrideData.path, null);
                        }
                        for (NModJsonBean.JsonOverrideData jsonOverrideData : gameSupport.json_overrides) {
                            listener.onLoadNModAsset(jsonOverrideData.path);
                            JsonOverrider overrider = new JsonOverrider(new File(assetsDir, "assets"));
                            overrider.performOverride(new File(nmod.getGameSupportDir(gameSupport.name), "assets"), jsonOverrideData.path, jsonOverrideData.mode);
                        }
                        for (NModJsonBean.TextOverrideData textOverrideData : gameSupport.text_overrides) {
                            listener.onLoadNModAsset(textOverrideData.path);
                            TextOverrider overrider = new TextOverrider(new File(assetsDir, "assets"));
                            overrider.performOverride(new File(nmod.getGameSupportDir(gameSupport.name), "assets"), textOverrideData.path, textOverrideData.mode);
                        }

                        // Patch Libs
                        if (gameSupport.native_libs != null) {
                            if (nmod.getFileInGameSupportDir(targetArch, gameSupport.name).exists()) {
                                Patcher.patchNativeLibraryDir(context.getClassLoader(), nmod.getFileInGameSupportDir(targetArch, gameSupport.name).getAbsolutePath());
                                for (NModJsonBean.NativeLibData nativeLibData : gameSupport.native_libs) {
                                    listener.onLoadNModNativeLibrary(nmod, nativeLibData.name);
                                    System.loadLibrary(nativeLibData.name);
                                }
                            } else
                                throw new NModException("No target arch found.");
                        }

                    } catch (NModException exception) {
                        nModExceptions.add(exception);
                    } catch (Throwable throwable) {
                        nModExceptions.add(new NModException("Failed to load this NMod.", throwable));
                    }
                }
                listener.onLoadNModsFinish();
            }

            // Arrange
            listener.onArrange();
            patchAssetPath.add(fileEnvironment.getCodeCacheDirPathForAssets());
            Log.d("EnderCore-Launcher", "Game is initialized.");

        } catch (Throwable e) {
            listener.onSuspend();
            throw new LauncherException("Unexpected fatal error caused in game initialization.", e);
        }
        initializedGame = true;
        listener.onFinish();
        return nModExceptions;
    }

    public void startGame(Context context) throws LauncherException {
        if (!initializedGame)
            throw new RuntimeException("Game isn't initialized.Please initialize game (Launcher.initializeGame) before start game.");
        try {
            IFileEnvironment fileEnvironment = core.getFileEnvironment();
            File dir = new File(fileEnvironment.getCodeCacheDirPathForDex());
            FileUtils.copy(context.getAssets().open(ASSETS_FILE_AGENT_DEX), new File(dir, NAME_AGENT_DEX));
            if (!(new File(dir, NAME_AGENT_DEX)).setReadOnly()) { // Android 15 fix
                throw new LauncherException("Unable to set file to read-only: " + (new File(dir, NAME_AGENT_DEX)).getAbsolutePath());
            }
            Patcher.patchDexFile(context.getClassLoader(), new File(dir, NAME_AGENT_DEX).getAbsolutePath(), fileEnvironment.getCodeCacheDirPathForDexOpt());
            patchDexPath.add(new File(dir, NAME_AGENT_DEX).getAbsolutePath());
            (new File(dir, NAME_AGENT_DEX)).setWritable(true);
            DexClassLoader dexClassLoader = new DexClassLoader(new File(dir, NAME_AGENT_DEX).getAbsolutePath(), dir.getAbsolutePath(), null, context.getClass().getClassLoader());
            Class<?> activityClass = dexClassLoader.loadClass("com.mojang.minecraftpe.AgentMainActivity");
            Intent launchIntent = new Intent(context, activityClass);
            launchIntent.putExtra("ENDERCORE-PATCH-ASSETS", patchAssetPath);
            launchIntent.putExtra("ENDERCORE-PATCH-DEX", patchDexPath);
            launchIntent.putExtra("ENDERCORE-PATCH-LIBS", patchLibPath);
            launchIntent.putExtra("ENDERCORE-PATCH-OPT", fileEnvironment.getCodeCacheDirPathForDexOpt());
            context.startActivity(launchIntent);
            Log.d("EnderCore-Launcher", "Game started.");
        } catch (IOException | NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            throw new LauncherException("Start game failed.", e);
        }
    }

    public void setGameInitializationListener(IInitializationListener listener) {
        this.listener = listener;
    }

    private void loadMinecraftNativeLibrary(Context context, String gameVersionName) throws ClassNotFoundException {
        Log.d("EnderCore-Launcher", "MCPE version: " + gameVersionName);
        listener.onLoadNativeLibrary(NAME_MINECRAFTPE);

        if (isMcpeVersion(gameVersionName, "0.6.")) {
            Class.forName(CLASS_MAIN_ACTIVITY, true, context.getClassLoader());
            return;
        }

        //0.8 comp
        if (isMcpeVersion(gameVersionName, "0.8.", "0.9.")) {
            try (TemporaryEglContext ignored = TemporaryEglContext.create()) {
                Class.forName(CLASS_MAIN_ACTIVITY, true, context.getClassLoader());
            }
            return;
        }

        System.loadLibrary(LIB_MINECRAFTPE);
    }

    private static boolean isMcpeVersion(String versionName, String... prefixes) {
        if (versionName == null)
            return false;

        for (String prefix : prefixes) {
            if (versionName.startsWith(prefix))
                return true;
        }
        return false;
    }

    //0.8 comp
    private static final class TemporaryEglContext implements AutoCloseable {
        private final EGL10 egl;
        private final EGLDisplay display;
        private final EGLSurface surface;
        private final EGLContext context;
        private final EGLDisplay previousDisplay;
        private final EGLSurface previousDrawSurface;
        private final EGLSurface previousReadSurface;
        private final EGLContext previousContext;

        private TemporaryEglContext(EGL10 egl, EGLDisplay display, EGLSurface surface, EGLContext context,
                                    EGLDisplay previousDisplay, EGLSurface previousDrawSurface,
                                    EGLSurface previousReadSurface, EGLContext previousContext) {
            this.egl = egl;
            this.display = display;
            this.surface = surface;
            this.context = context;
            this.previousDisplay = previousDisplay;
            this.previousDrawSurface = previousDrawSurface;
            this.previousReadSurface = previousReadSurface;
            this.previousContext = previousContext;
        }

        static TemporaryEglContext create() {
            EGL10 egl = (EGL10) EGLContext.getEGL();
            EGLDisplay previousDisplay = egl.eglGetCurrentDisplay();
            EGLSurface previousDrawSurface = egl.eglGetCurrentSurface(EGL10.EGL_DRAW);
            EGLSurface previousReadSurface = egl.eglGetCurrentSurface(EGL10.EGL_READ);
            EGLContext previousContext = egl.eglGetCurrentContext();

            EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (display == EGL10.EGL_NO_DISPLAY)
                throw new IllegalStateException("Unable to get EGL display.");

            int[] version = new int[2];
            if (!egl.eglInitialize(display, version))
                throw new IllegalStateException("Unable to initialize EGL.");

            EGLConfig config = chooseConfig(egl, display);
            EGLSurface surface = createSurface(egl, display, config);
            EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, new int[]{EGL10.EGL_NONE});
            if (context == EGL10.EGL_NO_CONTEXT) {
                egl.eglDestroySurface(display, surface);
                egl.eglTerminate(display);
                throw new IllegalStateException("Unable to create EGL context.");
            }

            if (!egl.eglMakeCurrent(display, surface, surface, context)) {
                egl.eglDestroyContext(display, context);
                egl.eglDestroySurface(display, surface);
                egl.eglTerminate(display);
                throw new IllegalStateException("Unable to make temporary EGL context current.");
            }

            return new TemporaryEglContext(egl, display, surface, context, previousDisplay,
                    previousDrawSurface, previousReadSurface, previousContext);
        }

        private static EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] configAttributes = {
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL10.EGL_RENDERABLE_TYPE, 1,
                    EGL10.EGL_RED_SIZE, 5,
                    EGL10.EGL_GREEN_SIZE, 6,
                    EGL10.EGL_BLUE_SIZE, 5,
                    EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] configCount = new int[1];
            if (!egl.eglChooseConfig(display, configAttributes, configs, configs.length, configCount)
                    || configCount[0] == 0) {
                throw new IllegalStateException("Unable to choose EGL config.");
            }
            return configs[0];
        }

        private static EGLSurface createSurface(EGL10 egl, EGLDisplay display, EGLConfig config) {
            int[] surfaceAttributes = {
                    EGL10.EGL_WIDTH, 1,
                    EGL10.EGL_HEIGHT, 1,
                    EGL10.EGL_NONE
            };
            EGLSurface surface = egl.eglCreatePbufferSurface(display, config, surfaceAttributes);
            if (surface == EGL10.EGL_NO_SURFACE)
                throw new IllegalStateException("Unable to create EGL pbuffer surface.");

            return surface;
        }

        @Override
        public void close() {
            if (previousDisplay != EGL10.EGL_NO_DISPLAY && previousContext != EGL10.EGL_NO_CONTEXT) {
                egl.eglMakeCurrent(previousDisplay, previousDrawSurface, previousReadSurface, previousContext);
            } else {
                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            }
            egl.eglDestroyContext(display, context);
            egl.eglDestroySurface(display, surface);
            egl.eglTerminate(display);
        }
    }
}
