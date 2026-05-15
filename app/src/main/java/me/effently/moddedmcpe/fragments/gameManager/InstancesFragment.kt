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
import me.effently.moddedmcpe.InitializingActivity
import me.effently.moddedmcpe.R
import org.endercore.android.EnderCore
import org.endercore.android.operator.instance.InstanceOperator
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.InstanceState

const val INSTALLED_MCPE_INSTANCE_ID = "installed-minecraftpe"

class InstancesFragment : Fragment() {
    private lateinit var recyclerInstances: RecyclerView
    private lateinit var adapter: InstancesAdapter
    private val instances = mutableListOf<GameInstance>()
    private lateinit var operator: InstanceOperator

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
        try {
            operator.duplicateInstance(instance)
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
                operator.deleteInstance(instance.id)
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
