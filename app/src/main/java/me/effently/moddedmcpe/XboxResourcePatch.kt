package me.effently.moddedmcpe

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.ContextThemeWrapper
import org.endercore.android.EnderCore
import org.endercore.android.operator.GamePackageManager
import org.endercore.android.operator.instance.InstanceOperator
import org.endercore.android.utils.LaunchContext
import java.util.concurrent.ConcurrentHashMap

internal object XboxResourcePatch {
    private const val TAG = "XboxResPatch"
    private const val CLIENT_PACKAGE_NAME_KEY = "com.microsoft.onlineid.client_package_name"
    private val PATCHED_ACTIVITY_PREFIXES = arrayOf(
        "com.microsoft.xbox.", "com.microsoft.onlineid.", "com.microsoft.xal.", "com.microsoft.xboxtcui.", "com.facebook.FacebookActivity"
    )

    private val resourceCache = ConcurrentHashMap<String, Resources>()
    @Volatile private var installed = false

    fun install(appContext: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                val instrumentationField = activityThreadClass.getDeclaredField("mInstrumentation").apply { isAccessible = true }
                val currentInstrumentation = instrumentationField.get(currentThread) as Instrumentation
                if (currentInstrumentation !is HookedInstrumentation) {
                    instrumentationField.set(currentThread, HookedInstrumentation(appContext.applicationContext, currentInstrumentation))
                }
                installed = true
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to install", e)
            }
        }
    }

    private fun patchActivity(appContext: Context, activity: Activity) {
        val className = activity.javaClass.name
        if (!PATCHED_ACTIVITY_PREFIXES.any { className.startsWith(it) }) return

        try {
            val core = EnderCore.getInstance()
            val instanceId = LaunchContext.getInstanceId(activity.intent) ?: core.fileEnvironment.activeWorkspaceId
            if (instanceId.isNullOrBlank()) return
            
            core.fileEnvironment.setActiveWorkspace(instanceId)
            val instance = InstanceOperator(appContext, core.fileEnvironment).repository.getInstance(instanceId) ?: return
            val apkPath = InstanceOperator(appContext, core.fileEnvironment).resolveGamePackage(instance).baseApkPath
            
            activity.intent?.putExtra(CLIENT_PACKAGE_NAME_KEY, GamePackageManager.PACKAGE_NAME)

            val resources = resourceCache.getOrPut(apkPath) {
                val assetManager = AssetManager::class.java.newInstance()
                AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assetManager, apkPath)
                val original = activity.resources
                Resources(assetManager, original.displayMetrics, original.configuration)
            }

            ContextThemeWrapper::class.java.getDeclaredField("mResources").apply { isAccessible = true }.set(activity, resources)
            ContextThemeWrapper::class.java.getDeclaredField("mTheme").apply { isAccessible = true }.set(activity, null)

            val spec = getPatchSpec(className)
            val themeId = if (spec.theme != null) resources.getIdentifier(spec.theme, "style", GamePackageManager.PACKAGE_NAME) else 0

            val activityInfoField = Activity::class.java.getDeclaredField("mActivityInfo").apply { isAccessible = true }
            val info = activityInfoField.get(activity) as? ActivityInfo
            if (info != null) {
                if (themeId != 0) info.theme = themeId
                if (spec.label != null) {
                    val labelId = resources.getIdentifier(spec.label, "string", GamePackageManager.PACKAGE_NAME)
                    if (labelId != 0) {
                        info.labelRes = labelId
                        info.nonLocalizedLabel = null
                    }
                }
                if (spec.icon != null) {
                    val iconId = resources.getIdentifier(spec.icon, "drawable", GamePackageManager.PACKAGE_NAME)
                    if (iconId != 0) {
                        info.icon = iconId; info.logo = iconId
                        info.applicationInfo.icon = iconId; info.applicationInfo.logo = iconId
                    } else {
                        info.icon = 0; info.logo = 0
                        info.applicationInfo.icon = 0; info.applicationInfo.logo = 0
                    }
                }
            }
            if (themeId != 0) activity.setTheme(themeId)
            
        } catch (e: Throwable) {
            Log.e(TAG, "Patch failed for $className", e)
        }
    }

    private data class Spec(val theme: String? = null, val label: String? = null, val icon: String? = null)
    private fun getPatchSpec(className: String): Spec {
        val msaSpec = Spec("Theme.MSA", "webflow_header", "msa_ms_logo")
        return when (className) {
            "com.microsoft.xbox.idp.ui.AuthFlowActivity", "com.microsoft.xbox.idp.ui.ErrorActivity",
            "com.microsoft.onlineid.interop.xbox.ui.SignUpActivity", "com.microsoft.onlineid.interop.xbox.ui.SignInErrorActivity",
            "com.microsoft.onlineid.interop.xbox.ui.XUIDCreationErrorActivity", "com.microsoft.onlineid.interop.xbox.ui.WelcomeActivity" -> Spec("OnlineidUiTheme")
            "com.microsoft.onlineid.authenticator.AccountAddPendingActivity", "com.microsoft.onlineid.internal.ui.WebFlowActivity",
            "com.microsoft.onlineid.ui.AddAccountActivity", "com.microsoft.onlineid.internal.ui.InterruptResolutionActivity",
            "com.microsoft.xal.browser.BrowserLaunchActivity", "com.microsoft.xal.browser.WebKitWebViewController" -> msaSpec
            "com.microsoft.onlineid.ui.SignOutActivity" -> Spec("Theme.MSA.Transparent")
            "com.microsoft.onlineid.internal.ui.AccountPickerActivity" -> Spec("Theme.MSA.Dialog", "webflow_header", "msa_ms_logo")
            else -> Spec()
        }
    }

    private class HookedInstrumentation(val appContext: Context, val delegate: Instrumentation) : Instrumentation() {
        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            patchActivity(appContext, activity)
            delegate.callActivityOnCreate(activity, icicle)
        }
        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?, persistentState: PersistableBundle?) {
            patchActivity(appContext, activity)
            delegate.callActivityOnCreate(activity, icicle, persistentState)
        }
        override fun callActivityOnResume(activity: Activity) = delegate.callActivityOnResume(activity)
    }
}
