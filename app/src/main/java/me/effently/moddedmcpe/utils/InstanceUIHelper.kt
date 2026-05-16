package me.effently.moddedmcpe.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import me.effently.moddedmcpe.R
import org.endercore.android.operator.instance.InstanceOperator
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.RemoteVersion

object InstanceUIHelper {

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, activity.getString(R.string.btn_cancel)) { _, _ ->
            cancelAction.run()
            Toast.makeText(activity, R.string.toast_cancelled, Toast.LENGTH_SHORT).show()
            onFinish()
        }

        dialog.show()
    }
}
