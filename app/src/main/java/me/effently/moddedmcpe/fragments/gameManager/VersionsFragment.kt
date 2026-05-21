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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
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
    private lateinit var adapter: VersionsAdapter
    private lateinit var operator: InstanceOperator

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_versions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        operator = InstanceOperator(requireContext(), EnderCore.instance.fileEnvironment)

        recyclerVersions = view.findViewById(R.id.recycler_versions)
        val btnHelpArch = view.findViewById<Button>(R.id.btn_help_arch)

        recyclerVersions.layoutManager = LinearLayoutManager(context)
        adapter = VersionsAdapter { version -> onVersionClick(version) }
        recyclerVersions.adapter = adapter

        btnHelpArch.setOnClickListener {
            showArchHelpDialog()
        }

        loadVersions()
    }

    private fun showArchHelpDialog() {
        val is64Bit = android.os.Process.is64Bit()
        val archName = if (is64Bit) "ARM64 (armv8)" else "ARM32 (armv7a)"
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_arch_help_title, getString(R.string.title_versions), archName))
            .setMessage(getString(R.string.dialog_arch_help_message, archName))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun onVersionClick(version: RemoteVersion) {
        val versionInstances = operator.repository.instances.filter { it.source?.url == version.url }
        if (versionInstances.isEmpty()) { startDownload(version); return }

        val actions = listOf(
            getString(R.string.btn_install_new_instance) to { startDownload(version) },
            getString(R.string.btn_play_existing) to { playExisting(versionInstances) },
            getString(R.string.btn_delete_all_for_version) to { deleteInstances(version, versionInstances) }
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
                // update remote version element (if needed.. now its not)
                val pos = adapter.currentList.indexOf(version)
                if (pos != -1) adapter.notifyItemChanged(pos)
            }
        }
    }

    private fun deleteInstances(version: RemoteVersion, versionInstances: List<GameInstance>) {
        activity?.let { act ->
            InstanceUIHelper.deleteInstances(act, operator, versionInstances) {
                // update remote version element (if needed.. now its not)
                val pos = adapter.currentList.indexOf(version)
                if (pos != -1) adapter.notifyItemChanged(pos)
            }
        }
    }

    private fun loadVersions() {
        val repository = RemoteVersionRepository(EnderCore.instance.fileEnvironment)

        lifecycleScope.launch(Dispatchers.IO) {
            val remoteVersions = repository.fetchVersions(android.os.Process.is64Bit())
            withContext(Dispatchers.Main) {
                adapter.submitList(remoteVersions.reversed())

                if (remoteVersions.isEmpty()) {
                    Toast.makeText(context, R.string.toast_versions_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    class VersionsAdapter(
        private val onDownloadClick: (RemoteVersion) -> Unit
    ) : ListAdapter<RemoteVersion, VersionsAdapter.ViewHolder>(DIFF) {

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<RemoteVersion>() {
                override fun areItemsTheSame(a: RemoteVersion, b: RemoteVersion) = a.url == b.url
                override fun areContentsTheSame(a: RemoteVersion, b: RemoteVersion) = a.url == b.url && a.name == b.name && a.isNotTested == b.isNotTested
            }
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textName: TextView = view.findViewById(R.id.text_version_name)
            val textStatus: TextView = view.findViewById(R.id.text_version_status)
            val btnDownload: Button = view.findViewById(R.id.btn_download)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_remote_version, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val version = getItem(position)
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
    }
}
