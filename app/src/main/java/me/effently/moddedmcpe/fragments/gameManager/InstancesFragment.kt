package me.effently.moddedmcpe.fragments.gameManager

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import me.effently.moddedmcpe.InitializingActivity
import me.effently.moddedmcpe.R
import org.endercore.android.EnderCore
import org.endercore.android.operator.instance.InstanceRepository
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.InstanceSource
import org.endercore.android.operator.instance.model.InstanceSourceType
import org.endercore.android.operator.instance.model.InstanceState
import org.endercore.android.utils.FileUtils
import java.io.File
import java.util.UUID

class InstancesFragment : Fragment() {
    private lateinit var recyclerInstances: RecyclerView
    private lateinit var adapter: InstancesAdapter
    private val instances = mutableListOf<GameInstance>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_instances, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        val repo = InstanceRepository(EnderCore.instance.fileEnvironment)
        val loadedInstances = repo.instances

        instances.clear()
        instances.addAll(loadedInstances)
        adapter.notifyDataSetChanged()

        if (instances.isEmpty()) {
            Toast.makeText(context, R.string.toast_no_instances, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInstanceOptions(instance: GameInstance) {
        val options = arrayOf("Play", "Duplicate", "Delete")
        AlertDialog.Builder(requireContext())
            .setTitle(instance.name)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Play" -> playInstance(instance)
                    "Duplicate" -> duplicateInstance(instance)
                    "Delete" -> deleteInstance(instance)
                }
            }
            .show()
    }

    private fun duplicateInstance(instance: GameInstance) {
        val repo = InstanceRepository(EnderCore.instance.fileEnvironment)
        try {
            val source = instance.source ?: throw IllegalStateException("Instance source is missing.")
            val newId = UUID.randomUUID().toString().substring(0, 8)
            val duplicate = GameInstance().apply {
                id = newId
                name = "${instance.name} (Copy)"
                state = InstanceState.REBUILD_REQUIRED
                createdAt = System.currentTimeMillis()
                settings = instance.settings?.deepCopy() ?: JsonObject()
                packageSnapshot = instance.packageSnapshot
            }

            when (source.type) {
                InstanceSourceType.MANAGED_APK -> {
                    FileUtils.copy(managedApkFile(repo, instance.id), managedApkFile(repo, newId))
                    duplicate.source = InstanceSource(InstanceSourceType.MANAGED_APK).apply {
                        origin = source.origin
                        url = source.url
                        label = source.label
                    }
                }
                InstanceSourceType.INSTALLED_PACKAGE -> {
                    val gamePackage = EnderCore.instance.gamePackageManager.gamePackage
                        ?: throw IllegalStateException("Installed MCPE package is not available.")
                    FileUtils.copy(File(gamePackage.baseApkPath), managedApkFile(repo, newId))
                    duplicate.source = InstanceSource(InstanceSourceType.MANAGED_APK).apply {
                        origin = "installed_copy"
                    }
                    duplicate.packageSnapshot = packageSnapshotOf(gamePackage)
                }
                InstanceSourceType.EXTERNAL_APK -> {
                    duplicate.source = InstanceSource(InstanceSourceType.EXTERNAL_APK).apply {
                        apkPath = source.apkPath
                    }
                }
                InstanceSourceType.REMOTE_APK -> {
                    throw IllegalStateException("Remote APK is not downloaded yet.")
                }
            }

            repo.saveInstance(duplicate)
            loadInstances()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to duplicate: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun playInstance(instance: GameInstance) {
        if (instance.state == InstanceState.DOWNLOADING) {
            Toast.makeText(context, R.string.toast_downloading, Toast.LENGTH_SHORT).show()
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
                val repo = InstanceRepository(EnderCore.instance.fileEnvironment)
                repo.deleteInstance(instance.id)
                loadInstances()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
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
            holder.textStatus.text = getString(R.string.text_status, instance.state.toString())
            holder.itemView.setOnClickListener { onItemClick(instance) }
            holder.btnPlay.setOnClickListener { onPlayClick(instance) }
            holder.btnDelete.setOnClickListener { onDeleteClick(instance) }
        }

        override fun getItemCount() = items.size
    }
}
