# ModdedMCPE Android
A simple MCPE launcher for Android that can load multiple versions at once.

ModdedMCPE is not related to ModdedPE or ModdedBE. It is designed to support multi-versions of Minecraft, and there're possibilities for it to become a cross-platform Minecraft Launcher.

- **Original work**:  
This is fork of [listerily ModdedBE](https://github.com/listerily/ModdedBE) work.

- **Status**:  
This isn't even the first version, not much is ready. I hope I have enough time continue developing..

- **Disclaimer**:  
NOT AN OFFICIAL MINECRAFT LAUNCHER/CLIENT, NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

## Run the code
 Download Android Studio, android sdk and android ndk.  
 or:
 ```
./gradlew :app:assembleDebug
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n me.effently.moddedmcpe/.MainActivity
```
 Build, install and run the app.

## What's Available
 + Launches MCPE 0.6 - 1.1 (Minecraft Bedrock 1.16.201.01? - not working now).
 + Auto license the game
 + Multi-version supports for Minecraft.
 + Install NMods.
 + Patch NMods.
 + Manage NMods.

NMods haven't beed tested in recent builds.
 
## TODO
maybe
 + Support for lower Android versions.
 + Support for lower and higher Minecraft versions.
 + Create documents and development tools for NMods.
 + Modding API Toolbox (JavaScript, C++)

## Support us
 + Read our code and helps us to improve it.
 + Join us as a contributor.
 + Help us solve the issues.
 
## Make Contributions
 + [Join our discord](https://discord.gg/sSNzZykqUR)
 + Send us pull requests.
 + Sign your name in the project files /app/src/res/values/strings.xml: tag "app_contributors".
 
