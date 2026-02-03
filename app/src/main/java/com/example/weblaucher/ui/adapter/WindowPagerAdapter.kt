package com.example.weblaucher.ui.adapter

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.weblaucher.model.WindowConfig
import com.example.weblaucher.webview.WebViewPool

class WindowPagerAdapter(
    private var windows: List<WindowConfig>,
    private val webViewPool: WebViewPool
) : RecyclerView.Adapter<WindowPagerAdapter.WindowHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WindowHolder {
        val container = FrameLayout(parent.context)
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return WindowHolder(container)
    }

    override fun onBindViewHolder(holder: WindowHolder, position: Int) {
        val window = windows[position]
        webViewPool.attachTo(window, holder.container)
    }

    override fun onViewRecycled(holder: WindowHolder) {
        val position = holder.bindingAdapterPosition
        if (position in windows.indices) {
            val window = windows[position]
            webViewPool.detach(window.id)
        }
    }

    override fun getItemCount(): Int = windows.size

    fun updateWindows(list: List<WindowConfig>) {
        windows = list
        notifyDataSetChanged()
    }

    class WindowHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)
}
