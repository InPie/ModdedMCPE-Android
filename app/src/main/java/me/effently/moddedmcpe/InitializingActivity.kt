package me.effently.moddedmcpe

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.endercore.android.EnderCore
import org.endercore.android.exception.LauncherException
import org.endercore.android.interf.implemented.InitializationListener
import org.endercore.android.operator.ApkGamePackageManager
import org.endercore.android.operator.GamePackage
import org.endercore.android.operator.instance.GamePackageBuilder
import org.endercore.android.operator.instance.InstanceRepository
import org.endercore.android.operator.instance.NModPreparer
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.InstanceSourceType
import org.endercore.android.operator.instance.model.InstanceState
import me.effently.moddedmcpe.fragments.gameManager.isInstancePrepared
import me.effently.moddedmcpe.fragments.gameManager.managedApkFile
import me.effently.moddedmcpe.fragments.gameManager.packageSnapshotOf
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class InitializingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_initializing)
        object : Thread() {
            override fun run() {
                super.run()
                EnderCore.instance.launcher.setGameInitializationListener(object : InitializationListener() {
                    override fun onFinish() {
                        val finishMessage = Message()
                        finishMessage.what = LAUNCH_FINISH
                        handler.sendMessage(finishMessage)
                    }
                })
                try {
                    val core = EnderCore.instance
                    val instanceId = intent.getStringExtra("INSTANCE_ID")
                        ?: throw LauncherException("Instance id is missing.")
                    val repo = InstanceRepository(core.fileEnvironment)
                    val instance = repo.getInstance(instanceId)
                        ?: throw LauncherException("Instance not found: $instanceId")
                    val launchTarget = prepareInstance(repo, instance)

                    core.launcher.initializeGame(this@InitializingActivity, launchTarget)
                } catch (e: LauncherException) {
                    val errorMessage = Message()
                    errorMessage.what = LAUNCH_SUSPEND
                    errorMessage.obj = e
                    handler.sendMessage(errorMessage)
                } catch (e: Exception) {
                    val errorMessage = Message()
                    errorMessage.what = LAUNCH_SUSPEND
                    errorMessage.obj = LauncherException("Failed to prepare game launch.", e)
                    handler.sendMessage(errorMessage)
                }
            }
        }.start()
    }

    override fun onBackPressed() {
        Toast.makeText(this, R.string.app_loading_summary, Toast.LENGTH_LONG).show()
    }

    fun startGameActivity() {
        try {
            EnderCore.instance.launcher.startGame(this)
            finish()
        } catch (e: LauncherException) {
            startFatalActivity(e)
        }
    }

    fun startFatalActivity(exception: LauncherException) {
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        exception.printStackTrace(printWriter)
        val activityIntent = Intent(this, FatalActivity::class.java)
        activityIntent.putExtra(FatalActivity.TAG_FATAL_MESSAGES, writer.toString())
        startActivity(activityIntent)
        exception.printStackTrace()
        finish()
    }

    private val handler = MHandler(this)

    private fun prepareInstance(repo: InstanceRepository, instance: GameInstance): GamePackage {
        val core = EnderCore.instance
        val gamePackage = resolveGamePackage(repo, instance)
        val builder = GamePackageBuilder(core.fileEnvironment, instance.id)
        val preparer = NModPreparer(core.fileEnvironment, core.nModManager, instance.id)

        if (instance.state != InstanceState.READY || !isInstancePrepared(core.fileEnvironment, instance.id)) {
            builder.build(gamePackage)
            instance.state = InstanceState.READY
        }

        preparer.prepare(gamePackage)
        instance.packageSnapshot = packageSnapshotOf(gamePackage)
        instance.lastPlayedAt = System.currentTimeMillis()
        repo.saveInstance(instance)
        return gamePackage
    }

    private fun resolveGamePackage(repo: InstanceRepository, instance: GameInstance): GamePackage {
        val source = instance.source ?: throw LauncherException("Instance source is missing: ${instance.id}")
        return when (source.type) {
            InstanceSourceType.MANAGED_APK -> {
                val apkFile = managedApkFile(repo, instance.id)
                ApkGamePackageManager.getGamePackageFromApk(this, apkFile)
            }
            InstanceSourceType.EXTERNAL_APK -> {
                val apkPath = source.apkPath ?: throw LauncherException("External APK path is missing: ${instance.id}")
                ApkGamePackageManager.getGamePackageFromApk(this, File(apkPath))
            }
            InstanceSourceType.INSTALLED_PACKAGE -> {
                val gamePackage = EnderCore.instance.gamePackageManager.gamePackage
                    ?: throw LauncherException("Installed MCPE package is not available.")
                gamePackage
            }
            InstanceSourceType.REMOTE_APK -> {
                throw LauncherException("Remote instance is not downloaded yet: ${instance.name}")
            }
            else -> throw LauncherException("Unsupported instance source: ${source.type}")
        }
    }

    private class MHandler(private val context: InitializingActivity) : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                LAUNCH_FINISH -> context.startGameActivity()
                LAUNCH_SUSPEND -> context.startFatalActivity(msg.obj as LauncherException)
            }
        }
    }

    companion object {
        private const val LAUNCH_FINISH = 0
        private const val LAUNCH_SUSPEND = 1
    }
}
