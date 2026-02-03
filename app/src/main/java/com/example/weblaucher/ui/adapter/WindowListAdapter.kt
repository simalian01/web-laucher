package com.example.weblaucher.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.weblaucher.databinding.ItemWindowBinding
import com.example.weblaucher.model.WindowConfig

class WindowListAdapter(
    private var windows: MutableList<WindowConfig>,
    private val onEdit: (WindowConfig) -> Unit,
    private val onDelete: (WindowConfig) -> Unit
) : RecyclerView.Adapter<WindowListAdapter.WindowHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WindowHolder {
        val binding = ItemWindowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WindowHolder(binding)
    }

    override fun onBindViewHolder(holder: WindowHolder, position: Int) {
        holder.bind(windows[position])
    }

    override fun getItemCount(): Int = windows.size

    fun update(list: List<WindowConfig>) {
        windows = list.toMutableList()
        notifyDataSetChanged()
    }

    fun onMove(from: Int, to: Int) {
        if (from == to) return
        val item = windows.removeAt(from)
        windows.add(to, item)
        notifyItemMoved(from, to)
    }

    fun currentList(): List<WindowConfig> = windows

    inner class WindowHolder(private val binding: ItemWindowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(window: WindowConfig) {
            binding.windowName.text = "${window.name} (${window.url})"
            binding.editButton.setOnClickListener { onEdit(window) }
            binding.deleteButton.setOnClickListener { onDelete(window) }
        }
    }
}
