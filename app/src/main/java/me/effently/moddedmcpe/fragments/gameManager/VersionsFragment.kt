package me.effently.moddedmcpe.fragments.gameManager

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.effently.moddedmcpe.InitializingActivity
import me.effently.moddedmcpe.R
import me.effently.moddedmcpe.utils.InstanceUIHelper
import org.endercore.android.EnderCore
import org.endercore.android.operator.instance.InstanceOperator
import org.endercore.android.operator.instance.RemoteVersionRepository
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.InstanceState
import org.endercore.android.operator.instance.model.RemoteVersion
import java.io.File

class VersionsFragment : Fragment() {
    private lateinit var recyclerVersions: RecyclerView
    private lateinit var btnImportApk: Button
    private lateinit var adapter: VersionsAdapter
    private val versions = mutableListOf<RemoteVersion>()
    private lateinit var operator: InstanceOperator

    private val importPackageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importPackage(it) }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_versions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        operator = InstanceOperator(requireContext(), EnderCore.instance.fileEnvironment)

        recyclerVersions = view.findViewById(R.id.recycler_versions)
        btnImportApk = view.findViewById(R.id.btn_import_apk)

        recyclerVersions.layoutManager = LinearLayoutManager(context)
        adapter = VersionsAdapter(versions) { version -> onVersionClick(version) }
        recyclerVersions.adapter = adapter

        btnImportApk.setOnClickListener {
            pickPackageFile()
        }

        loadVersions()
    }

    private fun onVersionClick(version: RemoteVersion) {
        val versionInstances = operator.repository.instances.filter { it.source?.url == version.url }
        if (versionInstances.isEmpty()) { startDownload(version); return }

        val actions = listOf(
            getString(R.string.btn_install_new_instance) to { startDownload(version) },
            getString(R.string.btn_play_existing) to { playExisting(versionInstances) },
            getString(R.string.btn_delete_all_for_version) to {
                versionInstances.forEach { operator.deleteInstance(it.id) }
                Toast.makeText(context, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
            }
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_select_action_title, version.name))
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    private fun playExisting(versionInstances: List<GameInstance>) {
        if (versionInstances.size == 1) {
            playInstance(versionInstances[0])
            return
        }

        val instanceNames = versionInstances.map { "${it.name} (${it.id})" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_select_instance_title))
            .setItems(instanceNames) { _, idx -> playInstance(versionInstances[idx]) }
            .show()
    }

    private fun playInstance(instance: GameInstance) {
        if (instance.state == InstanceState.DOWNLOAD_FAILED) {
            activity?.let { act ->
                InstanceUIHelper.retryDownload(act, operator, instance) {}
            }
            return
        }

        if (!operator.canLaunch(instance)) {
            Toast.makeText(context, R.string.toast_instance_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(context, InitializingActivity::class.java)
        intent.putExtra("INSTANCE_ID", instance.id)
        startActivity(intent)
    }

    private fun startDownload(version: RemoteVersion) {
        activity?.let { act ->
            InstanceUIHelper.startDownload(act, operator, version) {
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun pickPackageFile() {
        importPackageLauncher.launch(
            arrayOf(
                "application/vnd.android.package-archive",
                "application/zip",
                "application/octet-stream"
            )
        )
    }

    private fun importPackage(uri: Uri) {
        val context = requireContext()
        val name = getDisplayName(uri) ?: "package.apk"
        val extension = when {
            name.endsWith(".zip", true) -> ".zip"
            name.endsWith(".apk", true) -> ".apk"
            context.contentResolver.getType(uri)?.contains("zip", ignoreCase = true) == true -> ".zip"
            else -> ".apk"
        }
        val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}$extension")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Failed to open selected file.")

                withContext(Dispatchers.Main) {
                    showImportDialog(tempFile, extension == ".zip")
                }
            } catch (e: Exception) {
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, getString(R.string.toast_import_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showImportDialog(tempFile: File, isZip: Boolean) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_download, null)
        val textStatus = dialogView.findViewById<TextView>(R.id.text_download_status)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)
        textStatus.setText(R.string.text_preparing)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (isZip) R.string.dialog_import_zip_title else R.string.dialog_import_apk_title)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val callback = object : InstanceOperator.InstallCallback {
            override fun onProgress(percent: Int) {
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    progressBar.progress = percent
                    textStatus.text = getString(R.string.text_download_progress, percent)
                }
            }

            override fun onSuccess() {
                tempFile.delete()
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    dialog.dismiss()
                    Toast.makeText(context, R.string.toast_import_success, Toast.LENGTH_LONG).show()
                }
            }

            override fun onError(e: Exception) {
                tempFile.delete()
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    dialog.dismiss()
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.dialog_import_failed_title)
                        .setMessage(e.message ?: "Unknown error occurred")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }

        dialog.show()
        if (isZip) {
            operator.importInstanceZip(tempFile, callback)
        } else {
            operator.importLocalApk(tempFile, true, callback)
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    private fun loadVersions() {
        val repository = RemoteVersionRepository(EnderCore.instance.fileEnvironment)

        lifecycleScope.launch(Dispatchers.IO) {
            val remoteVersions = repository.fetchVersions()
            withContext(Dispatchers.Main) {
                versions.clear()
                versions.addAll(remoteVersions)
                adapter.notifyDataSetChanged()

                if (versions.isEmpty()) {
                    Toast.makeText(context, R.string.toast_versions_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class VersionsAdapter(
        private val items: List<RemoteVersion>,
        private val onDownloadClick: (RemoteVersion) -> Unit
    ) : RecyclerView.Adapter<VersionsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textName: TextView = view.findViewById(R.id.text_version_name)
            val textStatus: TextView = view.findViewById(R.id.text_version_status)
            val btnDownload: Button = view.findViewById(R.id.btn_download)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_remote_version, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val version = items[position]
            val context = holder.itemView.context
            holder.textName.text = version.name

            if (version.isNotTested) {
                holder.textStatus.text = context.getString(R.string.text_download_status_untested)
                holder.textStatus.setTextColor(ContextCompat.getColor(context, R.color.mc_error))
            } else {
                holder.textStatus.text = context.getString(R.string.text_download_status_available)
                holder.textStatus.setTextColor(ContextCompat.getColor(context, R.color.mc_text_secondary))
            }

            holder.btnDownload.setOnClickListener { onDownloadClick(version) }
        }

        override fun getItemCount() = items.size
    }
}
