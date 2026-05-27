# ModdedMCPE Android
 A powerful Minecraft launcher for Android with multi-version support.  

**Features**:  
 + Multi-instance support.  
 + A basic list of external versions is available for download (for arm32 and arm64).  
 + Ability to import APK, import/export an instance with all worlds and settings.  
 + Each instance has its own dedicated space for worlds and settings.  
 + Data caching for fast game launch.  
 + Auto license the game.  
 + Install/Patch/Manage NMods.  
 *NMods haven't been tested in recent builds and are not completed rn.*  

Supported minecraft versions: (what has been tested and is working)  
 + Minecraft Pocket Edition: 0.1.1 - 1.1.5.1⠀(all known versions)  
 + Minecraft Bedrock: 1.16.201.01,⠀1.17.41.01  

 The launcher supports Android 7.1 to 16⠀([API](https://targetsdk.com/) 25-36)  
 
 [Download for Android](https://github.com/InPie/ModdedMCPE-Android/releases)⠀✧*｡  
 [Discord Server](https://discord.gg/sSNzZykqUR)  

## Some notes
- **Status**:  
 Further polishing and bug fixes are still needed. The launcher is not finished, but it is usable. Support for newer versions is welcome as long as support for older versions is not dropped.  

- **Credits**:  
 This launcher is fork of [listerily ModdedBE](https://github.com/listerily/ModdedBE) work. Without it, this launcher would not exist.  
ModdedMCPE is not related to ModdedPE or ModdedBE, it is designed to support all versions of legacy MCPE and some of Minecraft Bedrock.  

- **Disclaimer**:  
NOT AN OFFICIAL MINECRAFT LAUNCHER/CLIENT, NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.  

## Build & run
 To play, download the latest version from [releases](https://github.com/InPie/ModdedMCPE-Android/releases).  

 Download Android Studio, android sdk and android ndk.  
 Build, install and run the app.  
 or use commands:  
 ```
./gradlew :app:assembleDebug (or:)
./gradlew :app:assembleRelease

adb install -r -d app/build/outputs/apk/debug/app-debug.apk (or:)
adb install -r -d app/build/outputs/apk/release/app-release.apk

adb shell am start -n me.effently.moddedmcpe/.MainActivity
```
 
## TODO
 + Support for higher Minecraft versions.  
 + Create docs and development tools for NMods.  
 + Addons page: browse mods/maps/textures.  
 + Modding API Toolbox (JavaScript, C++)  

## Support us
 + Read our code and helps us to improve it.  
 + Join us as a contributor.  
 + Help us solve the issues.  
 
## Make Contributions
 + [Join our discord](https://discord.gg/sSNzZykqUR)
 + Send us pull requests.  
 + Sign your name in the project files /app/src/res/values/strings.xml: tag "app_contributors".  

## Other launchers
 + LeviLauncher is a launcher for **modern** Minecraft: Bedrock Edition for [Android (LeviLaunchroid)](https://github.com/LiteLDev/LeviLaunchroid) and for [Windows (simply LeviLauncher)](<https://github.com/LiteLDev/LeviLauncher)>).  
 + [Ninecraft](https://github.com/MCPI-Revival/Ninecraft) is mcpe 0.1.0 - 0.11.1 launcher for linux and windows (desktop).  
 + [NostalgiaLauncher](https://github.com/NLauncher/NostalgiaLauncherDesktop) is mcpe 0.1.0 - 0.11.1 launcher for linux and windows with advanced features, is based on Ninecraft.  
 + [MCLauncher](https://github.com/MCMrARM/mc-w10-version-launcher) is a bedrock launcher for windows.  
 + [BedrockLauncher](https://github.com/BedrockLauncher/BedrockLauncher) is another bedrock launcher for windows.  
 
