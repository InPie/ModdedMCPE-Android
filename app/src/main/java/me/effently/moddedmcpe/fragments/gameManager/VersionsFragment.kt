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
import org.endercore.android.operator.ApkGamePackageManager
import org.endercore.android.operator.instance.GamePackageBuilder
import org.endercore.android.operator.instance.InstanceDownloader
import org.endercore.android.operator.instance.InstanceRepository
import org.endercore.android.operator.instance.RemoteVersionRepository
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.InstanceSource
import org.endercore.android.operator.instance.model.InstanceSourceType
import org.endercore.android.operator.instance.model.InstanceState
import org.endercore.android.operator.instance.model.RemoteVersion
import java.io.File
import java.util.UUID

class VersionsFragment : Fragment() {
    private lateinit var recyclerVersions: RecyclerView
    private lateinit var btnImportApk: Button
    private lateinit var adapter: VersionsAdapter
    private val versions = mutableListOf<RemoteVersion>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_versions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        val instanceRepo = InstanceRepository(EnderCore.instance.fileEnvironment)
        val versionInstances = instancesForRemoteUrl(instanceRepo, version.url)

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
                        versionInstances.forEach { instanceRepo.deleteInstance(it.id) }
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

        val instanceId = generateUniqueId(version.name)
        val appContext = requireContext().applicationContext
        val fileEnvironment = EnderCore.instance.fileEnvironment
        val instanceRepo = InstanceRepository(fileEnvironment)

        val gameInstance = GameInstance().apply {
            id = instanceId
            name = version.name
            state = InstanceState.DOWNLOADING
            createdAt = System.currentTimeMillis()
            source = InstanceSource(InstanceSourceType.REMOTE_APK).apply {
                url = version.url
                label = version.name
            }
        }

        try {
            instanceRepo.saveInstance(gameInstance)
        } catch (e: Exception) {
            dialog.dismiss()
            Toast.makeText(context, getString(R.string.toast_instance_create_error, e.message), Toast.LENGTH_LONG).show()
            return
        }

        val apkFile = File(instanceRepo.getInstanceDir(instanceId), "apk/game.apk")
        val downloader = InstanceDownloader()

        downloader.downloadApk(version.url, apkFile, object : InstanceDownloader.DownloadListener {
            override fun onProgress(percent: Int) {
                activity?.runOnUiThread {
                    progressBar.progress = percent
                    textStatus.text = getString(R.string.text_download_progress, percent)
                }
            }

            override fun onSuccess(downloadedFile: File) {
                try {
                    gameInstance.state = InstanceState.PREPARING
                    gameInstance.source.type = InstanceSourceType.MANAGED_APK
                    gameInstance.source.origin = "remote"
                    instanceRepo.saveInstance(gameInstance)

                    activity?.runOnUiThread {
                        textStatus.text = getString(R.string.text_preparing)
                    }

                    val gamePackage = ApkGamePackageManager.getGamePackageFromApk(appContext, downloadedFile)
                    gameInstance.packageSnapshot = packageSnapshotOf(gamePackage)
                    // Future:.. if RemoteVersion gains sha256, compare it with packageSnapshot.apkSha256 here.
                    GamePackageBuilder(fileEnvironment, instanceId).build(gamePackage)
                    gameInstance.state = InstanceState.READY
                    instanceRepo.saveInstance(gameInstance)

                    activity?.runOnUiThread {
                        dialog.dismiss()
                        Toast.makeText(context, R.string.toast_download_success, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    gameInstance.state = InstanceState.PREPARE_FAILED
                    try {
                        instanceRepo.saveInstance(gameInstance)
                    } catch (ignored: Exception) {
                    }
                    activity?.runOnUiThread {
                        dialog.dismiss()
                        Toast.makeText(context, getString(R.string.toast_download_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onError(e: Exception) {
                activity?.runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(context, getString(R.string.toast_download_error, e.message), Toast.LENGTH_LONG).show()

                    gameInstance.state = InstanceState.DOWNLOAD_FAILED
                    try {
                        instanceRepo.saveInstance(gameInstance)
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }
        })
    }

    private fun generateUniqueId(name: String): String {
        return UUID.randomUUID().toString().substring(0, 8) + "-" + name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
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
