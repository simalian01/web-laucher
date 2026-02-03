package com.example.weblaucher.ui.drawer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.weblaucher.databinding.ItemAppBinding

class AppListAdapter(
    private var apps: List<AppInfo>,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppHolder(binding)
    }

    override fun onBindViewHolder(holder: AppHolder, position: Int) {
        val app = apps[position]
        holder.bind(app)
    }

    override fun getItemCount(): Int = apps.size

    fun update(list: List<AppInfo>) {
        apps = list
        notifyDataSetChanged()
    }

    inner class AppHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppInfo) {
            binding.appIcon.setImageDrawable(app.icon)
            binding.appName.text = app.name
            binding.root.setOnClickListener { onClick(app) }
        }
    }
}
