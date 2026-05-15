package me.effently.moddedmcpe.utils

import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import me.effently.moddedmcpe.R
import org.endercore.android.operator.instance.InstanceOperator
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.RemoteVersion

object InstanceUIHelper {

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }

    fun startDownload(fragment: Fragment, operator: InstanceOperator, version: RemoteVersion, onFinish: () -> Unit) {
        val context = fragment.requireContext()
        if (!isNetworkAvailable(context)) {
            showNoInternetDialog(context)
            return
        }

        showDownloadDialog(fragment, context) { callback ->
            operator.installRemoteVersion(version, callback)
        }?.let { 
             // mb needed to reload data in some cases but onSuccess already handles dismiss
             // on finish call provided by fragment
             // wrap the callback to call onFinish..
        }
    }

    fun retryDownload(fragment: Fragment, operator: InstanceOperator, instance: GameInstance, onFinish: () -> Unit) {
        val context = fragment.requireContext()
        if (!isNetworkAvailable(context)) {
            showNoInternetDialog(context)
            return
        }

        showDownloadDialog(fragment, context) { callback ->
            operator.retryInstance(instance, callback)
        }
    }

    private fun showNoInternetDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("No Internet")
            .setMessage("Please check your internet connection and try again.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showDownloadDialog(
        fragment: Fragment,
        context: Context,
        startAction: (InstanceOperator.InstallCallback) -> Runnable
    ): Runnable? {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download, null)
        val textStatus = dialogView.findViewById<TextView>(R.id.text_download_status)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Downloading...")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        var cancelAction: Runnable? = null

        val callback = object : InstanceOperator.InstallCallback {
            override fun onProgress(percent: Int) {
                fragment.activity?.runOnUiThread {
                    progressBar.progress = percent
                    textStatus.text = context.getString(R.string.text_download_progress, percent)
                }
            }

            override fun onSuccess() {
                fragment.activity?.runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(context, R.string.toast_download_success, Toast.LENGTH_LONG).show()
                    if (fragment is me.effently.moddedmcpe.fragments.gameManager.InstancesFragment) {
                         // hacky way, better use a generic onComplete
                         // just assume fragment might want to refresh =)
                    }
                }
            }

            override fun onError(e: Exception) {
                fragment.activity?.runOnUiThread {
                    dialog.dismiss()
                    if (e.message?.contains("cancelled") != true) {
                        AlertDialog.Builder(context)
                            .setTitle("Download Failed")
                            .setMessage(e.message ?: "Unknown error occurred")
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            }
        }

        cancelAction = startAction(callback)

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(R.string.btn_cancel)) { _, _ ->
            cancelAction?.run()
            Toast.makeText(context, R.string.toast_cancelled, Toast.LENGTH_SHORT).show()
        }

        dialog.show()
        return cancelAction
    }
}
