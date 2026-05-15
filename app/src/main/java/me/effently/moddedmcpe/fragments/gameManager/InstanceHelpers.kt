package me.effently.moddedmcpe.fragments.gameManager

import org.endercore.android.interf.IFileEnvironment
import org.endercore.android.operator.GamePackage
import org.endercore.android.operator.instance.InstanceRepository
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.PackageSnapshot
import java.io.File

const val INSTALLED_MCPE_INSTANCE_ID = "installed-minecraftpe"

fun managedApkFile(repository: InstanceRepository, instanceId: String): File {
    return File(repository.getInstanceDir(instanceId), "apk/game.apk")
}

fun isInstancePrepared(fileEnvironment: IFileEnvironment, instanceId: String): Boolean {
    val cacheDir = File(fileEnvironment.getInstanceCacheDirPath(instanceId))
    return File(cacheDir, "dex_libs/classes.dex").isFile &&
        File(cacheDir, "native_libs/libminecraftpe.so").isFile
}

fun packageSnapshotOf(gamePackage: GamePackage): PackageSnapshot {
    val apkFile = File(gamePackage.baseApkPath)
    return PackageSnapshot().apply {
        packageName = gamePackage.packageName
        versionName = gamePackage.versionName
        versionCode = gamePackage.versionCode
        apkSize = if (apkFile.exists()) apkFile.length() else 0
        // compare this with RemoteVersion.sha256 when the remote model exposes it
        //apkSha256 = ""
        //lastReadAt = System.currentTimeMillis()
    }
}

fun instancesForRemoteUrl(repository: InstanceRepository, remoteUrl: String): List<GameInstance> {
    return repository.instances.filter { it.source?.url == remoteUrl }
}
