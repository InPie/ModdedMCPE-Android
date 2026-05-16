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

const val INSTALLED_MCPE_INSTANCE_ID = "installed-minecraftpe"

class InstancesFragment : Fragment() {
    private lateinit var recyclerInstances: RecyclerView
    private lateinit var adapter: InstancesAdapter
    private val instances = mutableListOf<GameInstance>()
    private lateinit var operator: InstanceOperator
    private var pendingExportInstance: GameInstance? = null
    private val debugGson = GsonBuilder().setPrettyPrinting().create()

    private val exportZipLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            val instance = pendingExportInstance ?: return@registerForActivityResult
            pendingExportInstance = null
            uri?.let { doExport(instance, it) }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_instances, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        operator = InstanceOperator(requireContext(), EnderCore.instance.fileEnvironment)
        
        recyclerInstances = view.findViewById(R.id.recycler_instances)
        recyclerInstances.layoutManager = LinearLayoutManager(context)

        adapter = InstancesAdapter(
            instances,
            onPlayClick = { instance -> playInstance(instance) },
            onDeleteClick = { instance -> deleteInstance(instance) },
            onItemClick = { instance -> showInstanceOptions(instance) }
        )
        recyclerInstances.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadInstances()
    }

    private fun loadInstances() {
        val loadedInstances = operator.repository.instances

        instances.clear()
        instances.addAll(loadedInstances)
        adapter.notifyDataSetChanged()

        if (instances.isEmpty()) {
            Toast.makeText(context, R.string.toast_no_instances, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInstanceOptions(instance: GameInstance) {
        val actions = buildList {
            if (instance.state == InstanceState.DOWNLOAD_FAILED)
                add(R.string.btn_download to { retryDownload(instance) })
            else
                add(R.string.btn_play to { playInstance(instance) })
            add(R.string.btn_info to { showInstanceInfo(instance) })
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
        try {
            operator.duplicateInstance(instance)
            loadInstances()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to duplicate: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showInstanceInfo(instance: GameInstance) {
        val info = JsonObject()
        info.add("instance", debugGson.toJsonTree(instance))
        info.addProperty("instanceDir", operator.repository.getInstanceDir(instance.id).absolutePath)
        info.addProperty("managedApk", operator.getManagedApkFile(instance.id).absolutePath)
        info.addProperty("cacheDir", EnderCore.instance.fileEnvironment.getInstanceCacheDirPath(instance.id))
        info.addProperty("nmodsCacheDir", EnderCore.instance.fileEnvironment.getInstanceNModsCacheDirPath(instance.id))
        info.addProperty("prepared", operator.isInstancePrepared(instance.id))

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_instance_info_title)
            .setMessage(debugGson.toJson(info))
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.app_copy) { _, _ ->
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Instance info", debugGson.toJson(info)))
                Toast.makeText(context, R.string.app_copied, Toast.LENGTH_SHORT).show()
            }
            .show()
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
                operator.deleteInstance(instance.id)
                loadInstances()
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

    inner class InstancesAdapter(
        private val items: List<GameInstance>,
        private val onPlayClick: (GameInstance) -> Unit,
        private val onDeleteClick: (GameInstance) -> Unit,
        private val onItemClick: (GameInstance) -> Unit
    ) : RecyclerView.Adapter<InstancesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
            val instance = items[position]
            holder.textName.text = instance.name
            holder.textStatus.text = getInstanceStatusText(instance)
            holder.btnPlay.text = if (instance.state == InstanceState.DOWNLOAD_FAILED) {
                getString(R.string.btn_download)
            } else {
                getString(R.string.btn_play)
            }
            holder.itemView.setOnClickListener { onItemClick(instance) }
            holder.btnPlay.setOnClickListener { onPlayClick(instance) }
            holder.btnDelete.setOnClickListener { onDeleteClick(instance) }
        }

        override fun getItemCount() = items.size
    }

    private fun getInstanceStatusText(instance: GameInstance): String {
        if (instance.state != InstanceState.READY) {
            return getString(R.string.text_status, instance.state.toString())
        }

        val lastPlayedAt = instance.lastPlayedAt ?: return getString(R.string.text_never_launched)
        val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastPlayedAt))
        return getString(R.string.text_last_launch, formatted)
    }
}