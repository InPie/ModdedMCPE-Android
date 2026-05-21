package me.effently.moddedmcpe.fragments.gameManager

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.effently.moddedmcpe.InitializingActivity
import me.effently.moddedmcpe.R
import me.effently.moddedmcpe.utils.InstanceUIHelper
import org.endercore.android.EnderCore
import org.endercore.android.operator.instance.InstanceOperator
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.InstanceState
import java.io.File
import java.text.DateFormat
import java.util.Date

import android.provider.OpenableColumns
import org.endercore.android.operator.instance.model.InstanceSourceType

const val INSTALLED_MCPE_INSTANCE_ID = "installed-minecraftpe"

class InstancesFragment : Fragment() {
    private lateinit var recyclerInstances: RecyclerView
    private lateinit var adapter: InstancesAdapter
    private lateinit var operator: InstanceOperator
    private var pendingExportInstance: GameInstance? = null
    private val debugGson = GsonBuilder().setPrettyPrinting().create()

    private val exportZipLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            val instance = pendingExportInstance ?: return@registerForActivityResult
            pendingExportInstance = null
            uri?.let { doExport(instance, it) }
        }

    private val importPackageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importPackage(it) }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_instances, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        operator = InstanceOperator(requireContext(), EnderCore.instance.fileEnvironment)
        
        recyclerInstances = view.findViewById(R.id.recycler_instances)
        recyclerInstances.layoutManager = LinearLayoutManager(context)

        val btnImportApk = view.findViewById<Button>(R.id.btn_import_apk)
        btnImportApk.setOnClickListener {
            importPackageLauncher.launch(
                arrayOf(
                    "application/vnd.android.package-archive",
                    "application/zip",
                    "application/octet-stream"
                )
            )
        }

        adapter = InstancesAdapter(
            onPlayClick = { playInstance(it) },
            onDeleteClick = { deleteInstance(it) },
            onItemClick = { showInstanceOptions(it) }
        )
        recyclerInstances.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadInstances()
    }

    private fun loadInstances() {
        lifecycleScope.launch(Dispatchers.IO) {
            val loadedInstances = operator.repository.instances.toList()
            withContext(Dispatchers.Main) {
                adapter.submitList(loadedInstances)

                if (loadedInstances.isEmpty()) {
                    Toast.makeText(context, R.string.toast_no_instances, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showInstanceOptions(instance: GameInstance) {
        val actions = buildList {
            if (instance.state == InstanceState.DOWNLOAD_FAILED)
                add(R.string.btn_download to { retryDownload(instance) })
            else
                add(R.string.btn_play to { playInstance(instance) })
            add(R.string.btn_info to { showInstanceInfo(instance) })
            add(R.string.btn_rename to { renameInstance(instance) })
            add(R.string.btn_duplicate to { duplicateInstance(instance) })
            add(R.string.btn_export_zip to { exportInstance(instance) })
            add(R.string.btn_delete to { deleteInstance(instance) })
        }
        AlertDialog.Builder(requireContext())
            .setTitle(instance.name)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    private fun retryDownload(instance: GameInstance) {
        activity?.let { act ->
            InstanceUIHelper.retryDownload(act, operator, instance) {
                loadInstances()
            }
        }
    }

    private fun duplicateInstance(instance: GameInstance) {
        activity?.let { act ->
            InstanceUIHelper.duplicateInstance(act, operator, instance) {
                loadInstances()
            }
        }
    }

    private fun renameInstance(instance: GameInstance) {
        val input = android.widget.EditText(requireContext())
        input.setText(instance.name)
        input.setSelection(instance.name.length)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT
        input.maxLines = 1

        // styles
        val margin = (16 * requireContext().resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(margin, margin, margin, margin)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_rename_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != instance.name) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val updatedInstance = operator.repository.getInstance(instance.id) ?: instance
                        updatedInstance.name = newName
                        if (updatedInstance.settings == null) {
                            updatedInstance.settings = JsonObject()
                        }
                        updatedInstance.settings.addProperty("customName", true)
                        operator.repository.saveInstance(updatedInstance)
                        withContext(Dispatchers.Main) {
                            loadInstances()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showInstanceInfo(instance: GameInstance) {
        lifecycleScope.launch(Dispatchers.IO) {
            val versionName = instance.packageSnapshot?.versionName ?: "Unknown"
            val apkSizeMB = instance.packageSnapshot?.apkSize?.let { String.format(java.util.Locale.US, "%.2f MB", it / (1024.0 * 1024.0)) } ?: "Unknown"
            val sourceType = instance.source?.type?.name ?: "Unknown"

            val textInfo = """
                Name: ${instance.name}
                Version: $versionName
                APK Size: $apkSizeMB
                Source Type: $sourceType
                State: ${instance.state}
                Last played: ${if (instance.lastPlayedAt != null) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(instance.lastPlayedAt!!)) else "Never"}
                
                Paths:
                Dir: ${operator.repository.getInstanceDir(instance.id).absolutePath}
                APK: ${operator.getManagedApkFile(instance.id).absolutePath}
            """.trimIndent()

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_instance_info_title)
                    .setMessage(textInfo)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton("JSON") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val info = JsonObject()
                            info.add("instance", debugGson.toJsonTree(instance))
                            info.addProperty("instanceDir", operator.repository.getInstanceDir(instance.id).absolutePath)
                            info.addProperty("managedApk", operator.getManagedApkFile(instance.id).absolutePath)
                            info.addProperty("cacheDir", EnderCore.instance.fileEnvironment.getInstanceCacheDirPath(instance.id))
                            info.addProperty("nmodsCacheDir", EnderCore.instance.fileEnvironment.getInstanceNModsCacheDirPath(instance.id))
                            info.addProperty("prepared", operator.isInstancePrepared(instance))
                            val jsonStr = debugGson.toJson(info)
                            
                            withContext(Dispatchers.Main) {
                                if (!isAdded || context == null) return@withContext
                                AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.dialog_instance_info_title)
                                    .setMessage(jsonStr)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .setNegativeButton(R.string.app_copy) { _, _ ->
                                        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Instance info", jsonStr))
                                        Toast.makeText(context, R.string.app_copied, Toast.LENGTH_SHORT).show()
                                    }
                                    .show()
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun playInstance(instance: GameInstance) {
        if (instance.state == InstanceState.DOWNLOAD_FAILED) {
            retryDownload(instance)
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

    private fun deleteInstance(instance: GameInstance) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_delete_title, instance.name))
            .setMessage(R.string.dialog_delete_message)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                activity?.let { act ->
                    InstanceUIHelper.deleteInstances(act, operator, listOf(instance)) {
                        loadInstances()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun exportInstance(instance: GameInstance) {
        pendingExportInstance = instance
        exportZipLauncher.launch(
            "${instance.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.zip"
        )
    }

    private fun doExport(instance: GameInstance, uri: Uri) {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download, null)
        dialogView.findViewById<ProgressBar>(R.id.progress_bar).isIndeterminate = true
        dialogView.findViewById<TextView>(R.id.text_download_status).setText(R.string.text_exporting)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.dialog_exporting_title)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            
        dialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val error = try {
                val tempZip = File(context.cacheDir, "${instance.id}.zip")
                operator.exportInstanceZip(instance.id, tempZip)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    tempZip.inputStream().use { it.copyTo(out) }
                } ?: throw IllegalStateException("Failed to open export destination.")
                tempZip.delete()
                null
            } catch (e: Exception) { e }

            withContext(Dispatchers.Main) {
                dialog.dismiss()
                if (error == null) {
                    Toast.makeText(context, R.string.toast_export_success, Toast.LENGTH_LONG).show()
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.dialog_export_failed_title)
                        .setMessage(error.message ?: "Unknown error")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
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
        val isZip = extension == ".zip"

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download, null)
        val textStatus = dialogView.findViewById<TextView>(R.id.text_download_status)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)
        textStatus.setText(R.string.text_preparing)
        progressBar.isIndeterminate = true

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (isZip) R.string.dialog_import_zip_title else R.string.dialog_import_apk_title)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Failed to open selected file.")

                withContext(Dispatchers.Main) {
                    progressBar.isIndeterminate = false
                    val callback = object : InstanceOperator.InstallCallback {
                        override fun onProgress(percent: Int) {
                            activity?.runOnUiThread {
                                if (!isAdded) return@runOnUiThread
                                progressBar.progress = percent
                                textStatus.text = getString(R.string.text_import_progress, percent)
                            }
                        }

                        override fun onSuccess() {
                            tempFile.delete()
                            activity?.runOnUiThread {
                                if (!isAdded) return@runOnUiThread
                                dialog.dismiss()
                                Toast.makeText(context, R.string.toast_import_success, Toast.LENGTH_LONG).show()
                                loadInstances()
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

                    if (isZip) {
                        operator.importInstanceZip(tempFile, callback)
                    } else {
                        operator.importLocalApk(tempFile, true, callback)
                    }
                }
            } catch (e: Exception) {
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(context, getString(R.string.toast_import_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
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

    class InstancesAdapter(
        private val onPlayClick: (GameInstance) -> Unit,
        private val onDeleteClick: (GameInstance) -> Unit,
        private val onItemClick: (GameInstance) -> Unit
    ) : ListAdapter<GameInstance, InstancesAdapter.ViewHolder>(DIFF) {

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<GameInstance>() {
                override fun areItemsTheSame(a: GameInstance, b: GameInstance) = a.id == b.id
                override fun areContentsTheSame(a: GameInstance, b: GameInstance) = a.id == b.id && a.state == b.state && a.lastPlayedAt == b.lastPlayedAt && a.name == b.name
            }
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textName: TextView = view.findViewById(R.id.text_instance_name)
            val textStatus: TextView = view.findViewById(R.id.text_instance_status)
            val btnPlay: Button = view.findViewById(R.id.btn_play)
            val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_instance, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val instance = getItem(position)
            val context = holder.itemView.context
            holder.textName.text = instance.name
            holder.textStatus.text = getInstanceStatusText(context, instance)
            holder.btnPlay.text = context.getString(
                if (instance.state == InstanceState.DOWNLOAD_FAILED) R.string.btn_download else R.string.btn_play
            )
            holder.itemView.setOnClickListener { onItemClick(instance) }
            holder.btnPlay.setOnClickListener { onPlayClick(instance) }
            holder.btnDelete.setOnClickListener { onDeleteClick(instance) }
        }

        private fun getInstanceStatusText(context: android.content.Context, instance: GameInstance): String {
            if (instance.state != InstanceState.READY) {
                return context.getString(R.string.text_status, instance.state.toString())
            }

            val lastPlayedAt = instance.lastPlayedAt ?: return context.getString(R.string.text_never_launched)
            val formatted = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(lastPlayedAt))
            return context.getString(R.string.text_last_launch, formatted)
        }
    }
}
