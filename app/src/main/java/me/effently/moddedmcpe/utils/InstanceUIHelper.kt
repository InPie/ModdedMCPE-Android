package me.effently.moddedmcpe.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.effently.moddedmcpe.R
import org.endercore.android.operator.instance.InstanceOperator
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.RemoteVersion

object InstanceUIHelper {

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun startDownload(activity: Activity, operator: InstanceOperator, version: RemoteVersion, onFinish: () -> Unit) {
        if (!isNetworkAvailable(activity)) {
            showNoInternetDialog(activity)
            return
        }

        showDownloadDialog(activity, { callback ->
            operator.installRemoteVersion(version, callback)
        }, onFinish)
    }

    fun retryDownload(activity: Activity, operator: InstanceOperator, instance: GameInstance, onFinish: () -> Unit) {
        if (!isNetworkAvailable(activity)) {
            showNoInternetDialog(activity)
            return
        }

        showDownloadDialog(activity, { callback ->
            operator.retryInstance(instance, callback)
        }, onFinish)
    }

    private fun showNoInternetDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("No Internet")
            .setMessage("Please check your internet connection and try again.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showDownloadDialog(
        activity: Activity,
        startAction: (InstanceOperator.InstallCallback) -> Runnable,
        onFinish: () -> Unit = {}
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_download, null)
        val textStatus = dialogView.findViewById<TextView>(R.id.text_download_status)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.text_preparing)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val callback = object : InstanceOperator.InstallCallback {
            override fun onProgress(percent: Int) {
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    progressBar.progress = percent
                    textStatus.text = activity.getString(R.string.text_download_progress, percent)
                }
            }

            override fun onSuccess() {
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    dialog.dismiss()
                    Toast.makeText(activity, R.string.toast_download_success, Toast.LENGTH_LONG).show()
                    onFinish()
                }
            }

            override fun onError(e: Exception) {
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    dialog.dismiss()
                    onFinish()
                    if (e.message?.contains("cancelled") != true) {
                        AlertDialog.Builder(activity)
                            .setTitle(R.string.dialog_import_failed_title)
                            .setMessage(e.message ?: "Unknown error occurred")
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            }
        }

        val cancelAction = startAction(callback)

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getString(R.string.btn_cancel)) { _, _ ->
            cancelAction.run()
            Toast.makeText(activity, R.string.toast_cancelled, Toast.LENGTH_SHORT).show()
            onFinish()
        }

        dialog.show()
    }

    fun runActionWithProgress(
        activity: Activity,
        title: String,
        statusTextResId: Int,
        successToastResId: Int?,
        action: suspend () -> Unit,
        onFinish: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_download, null)
        dialogView.findViewById<ProgressBar>(R.id.progress_bar).isIndeterminate = true
        dialogView.findViewById<TextView>(R.id.text_download_status).setText(statusTextResId)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                action()
                withContext(Dispatchers.Main) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        dialog.dismiss()
                        if (successToastResId != null) {
                            Toast.makeText(activity, successToastResId, Toast.LENGTH_SHORT).show()
                        }
                        onFinish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        dialog.dismiss()
                        Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        onFinish()
                    }
                }
            }
        }
    }

    fun duplicateInstance(
        activity: Activity,
        operator: InstanceOperator,
        instance: GameInstance,
        onFinish: () -> Unit
    ) {
        runActionWithProgress(
            activity,
            activity.getString(R.string.btn_duplicate),
            R.string.text_duplicating,
            null,
            { operator.duplicateInstance(instance) },
            onFinish
        )
    }

    fun deleteInstances(
        activity: Activity,
        operator: InstanceOperator,
        instances: List<GameInstance>,
        onFinish: () -> Unit
    ) {
        if (instances.isEmpty()) return

        val title = if (instances.size == 1) activity.getString(R.string.dialog_delete_title, instances[0].name) else activity.getString(R.string.btn_delete)
        runActionWithProgress(
            activity,
            title,
            R.string.text_deleting,
            R.string.toast_deleted,
            { instances.forEach { operator.deleteInstance(it.id) } },
            onFinish
        )
    }
}
