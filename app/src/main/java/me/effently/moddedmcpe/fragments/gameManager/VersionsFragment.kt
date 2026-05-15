package me.effently.moddedmcpe.fragments.gameManager

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.effently.moddedmcpe.InitializingActivity
import me.effently.moddedmcpe.R
import org.endercore.android.EnderCore
import org.endercore.android.operator.instance.InstanceOperator
import org.endercore.android.operator.instance.RemoteVersionRepository
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.InstanceState
import org.endercore.android.operator.instance.model.RemoteVersion

class VersionsFragment : Fragment() {
    private lateinit var recyclerVersions: RecyclerView
    private lateinit var btnImportApk: Button
    private lateinit var adapter: VersionsAdapter
    private val versions = mutableListOf<RemoteVersion>()
    private lateinit var operator: InstanceOperator

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
            Toast.makeText(context, "Importing APK is not yet implemented", Toast.LENGTH_SHORT).show()
        }

        loadVersions()
    }

    private fun onVersionClick(version: RemoteVersion) {
        val versionInstances = operator.repository.instances.filter { it.source?.url == version.url }

        if (versionInstances.isEmpty()) {
            startDownload(version)
            return
        }

        val options = arrayOf("Install new instance", "Play existing", "Delete all for this version")
        AlertDialog.Builder(requireContext())
            .setTitle("Select action for ${version.name}")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Install new instance" -> startDownload(version)
                    "Play existing" -> playExisting(versionInstances)
                    "Delete all for this version" -> {
                        versionInstances.forEach { operator.deleteInstance(it.id) }
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun playExisting(versionInstances: List<GameInstance>) {
        if (versionInstances.size == 1) {
            playInstance(versionInstances[0])
            return
        }

        val instanceNames = versionInstances.map { "${it.name} (${it.id})" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select instance to play")
            .setItems(instanceNames) { _, idx -> playInstance(versionInstances[idx]) }
            .show()
    }

    private fun playInstance(instance: GameInstance) {
        if (instance.state == InstanceState.DOWNLOADING) {
            Toast.makeText(context, R.string.toast_downloading, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = android.content.Intent(context, InitializingActivity::class.java)
        intent.putExtra("INSTANCE_ID", instance.id)
        startActivity(intent)
    }

    private fun startDownload(version: RemoteVersion) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download, null)
        val textStatus = dialogView.findViewById<TextView>(R.id.text_download_status)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_download_title, version.name))
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                Toast.makeText(context, R.string.toast_cancelled, Toast.LENGTH_SHORT).show()
            }
            .show()

        operator.installRemoteVersion(version, object : InstanceOperator.InstallCallback {
            override fun onProgress(percent: Int) {
                activity?.runOnUiThread {
                    progressBar.progress = percent
                    textStatus.text = getString(R.string.text_download_progress, percent)
                }
            }

            override fun onSuccess() {
                activity?.runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(context, R.string.toast_download_success, Toast.LENGTH_LONG).show()
                }
            }

            override fun onError(e: Exception) {
                activity?.runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(context, getString(R.string.toast_download_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun loadVersions() {
        val repository = RemoteVersionRepository(EnderCore.instance.fileEnvironment)

        Thread {
            val remoteVersions = repository.fetchVersions()
            activity?.runOnUiThread {
                versions.clear()
                versions.addAll(remoteVersions)
                adapter.notifyDataSetChanged()

                if (versions.isEmpty()) {
                    Toast.makeText(context, R.string.toast_versions_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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
            holder.textName.text = version.name

            if (version.isNotTested) {
                holder.textStatus.text = getString(R.string.text_download_status_untested)
                holder.textStatus.setTextColor(android.graphics.Color.RED)
            } else {
                holder.textStatus.text = getString(R.string.text_download_status_available)
                holder.textStatus.setTextColor(android.graphics.Color.GRAY)
            }

            holder.btnDownload.setOnClickListener { onDownloadClick(version) }
        }

        override fun getItemCount() = items.size
    }
}
