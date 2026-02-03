package com.example.weblaucher.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.weblaucher.BuildConfig
import com.example.weblaucher.databinding.ActivitySettingsBinding
import com.example.weblaucher.databinding.DialogWindowEditBinding
import com.example.weblaucher.data.BackBehavior
import com.example.weblaucher.data.RefreshPolicy
import com.example.weblaucher.data.SettingsDataStore
import com.example.weblaucher.data.WindowRepository
import com.example.weblaucher.model.WindowConfig
import com.example.weblaucher.ui.adapter.WindowListAdapter
import com.example.weblaucher.util.ACTION_REFRESH_ALL
import com.example.weblaucher.util.ACTION_REFRESH_CURRENT
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var windowRepository: WindowRepository
    private lateinit var adapter: WindowListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsDataStore = SettingsDataStore(this)
        windowRepository = WindowRepository(this)

        lifecycleScope.launch {
            windowRepository.load()
            windowRepository.windows.collectLatest { list ->
                if (!::adapter.isInitialized) {
                    setupWindowList(list)
                } else {
                    adapter.update(list)
                }
            }
        }

        lifecycleScope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                binding.maxAliveInput.setText(settings.maxAlive.toString())
                when (RefreshPolicy.valueOf(settings.refreshPolicy)) {
                    RefreshPolicy.OFF -> binding.refreshOff.isChecked = true
                    RefreshPolicy.CURRENT -> binding.refreshCurrent.isChecked = true
                    RefreshPolicy.ALL -> binding.refreshAll.isChecked = true
                }
                binding.foregroundServiceSwitch.isChecked = settings.foregroundService
                binding.thirdPartyCookieSwitch.isChecked = settings.thirdPartyCookies
                binding.userAgentInput.setText(settings.userAgent)
                when (BackBehavior.valueOf(settings.backBehavior)) {
                    BackBehavior.WEB_BACK -> binding.backWeb.isChecked = true
                    BackBehavior.DISABLED -> binding.backDisabled.isChecked = true
                }
                binding.whitelistInput.setText(settings.whitelistDomains)
                binding.debugSwitch.isChecked = settings.debugWebView
            }
        }

        if (!BuildConfig.DEBUG) {
            binding.debugSwitch.isEnabled = false
        }

        binding.addWindow.setOnClickListener { showWindowDialog(null) }
        binding.refreshCurrentButton.setOnClickListener { sendBroadcast(Intent(ACTION_REFRESH_CURRENT)) }
        binding.refreshAllButton.setOnClickListener { sendBroadcast(Intent(ACTION_REFRESH_ALL)) }

        binding.refreshGroup.setOnCheckedChangeListener { _, checkedId ->
            lifecycleScope.launch {
                val policy = when (checkedId) {
                    binding.refreshOff.id -> RefreshPolicy.OFF
                    binding.refreshAll.id -> RefreshPolicy.ALL
                    else -> RefreshPolicy.CURRENT
                }
                settingsDataStore.updateRefreshPolicy(policy)
            }
        }

        binding.foregroundServiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settingsDataStore.updateForegroundService(isChecked) }
        }

        binding.thirdPartyCookieSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settingsDataStore.updateThirdPartyCookies(isChecked) }
        }

        binding.debugSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settingsDataStore.updateDebugWebView(isChecked) }
        }

        binding.backGroup.setOnCheckedChangeListener { _, checkedId ->
            lifecycleScope.launch {
                val behavior = if (checkedId == binding.backDisabled.id) BackBehavior.DISABLED else BackBehavior.WEB_BACK
                settingsDataStore.updateBackBehavior(behavior)
            }
        }

        binding.maxAliveInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.maxAliveInput.text.toString().toIntOrNull() ?: 5
                lifecycleScope.launch { settingsDataStore.updateMaxAlive(value) }
            }
        }

        binding.userAgentInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                lifecycleScope.launch { settingsDataStore.updateUserAgent(binding.userAgentInput.text.toString()) }
            }
        }

        binding.whitelistInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                lifecycleScope.launch { settingsDataStore.updateWhitelistDomains(binding.whitelistInput.text.toString()) }
            }
        }

        binding.defaultLauncherButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        }

        binding.batteryOptimizationButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun setupWindowList(list: List<WindowConfig>) {
        adapter = WindowListAdapter(list.toMutableList(), ::editWindow, ::deleteWindow)
        binding.windowList.layoutManager = LinearLayoutManager(this)
        binding.windowList.adapter = adapter
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.onMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                persistReorder()
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        helper.attachToRecyclerView(binding.windowList)
    }

    private fun persistReorder() {
        val reordered = adapter.currentList().mapIndexed { index, window -> window.copy(order = index) }
        lifecycleScope.launch { windowRepository.updateWindows(reordered) }
    }

    private fun showWindowDialog(window: WindowConfig?) {
        val dialogBinding = DialogWindowEditBinding.inflate(layoutInflater)
        if (window != null) {
            dialogBinding.windowNameInput.setText(window.name)
            dialogBinding.windowUrlInput.setText(window.url)
        }
        AlertDialog.Builder(this)
            .setTitle(if (window == null) "添加窗口" else "编辑窗口")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val name = dialogBinding.windowNameInput.text.toString().ifBlank { "窗口" }
                val url = dialogBinding.windowUrlInput.text.toString().ifBlank { "https://example.com" }
                val newWindow = window?.copy(name = name, url = url) ?: WindowConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    url = url,
                    order = adapter.currentList().size
                )
                val updated = adapter.currentList().toMutableList()
                if (window == null) {
                    updated.add(newWindow)
                } else {
                    val index = updated.indexOfFirst { it.id == window.id }
                    if (index >= 0) updated[index] = newWindow
                }
                lifecycleScope.launch { windowRepository.updateWindows(updated.mapIndexed { idx, item -> item.copy(order = idx) }) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun editWindow(window: WindowConfig) {
        showWindowDialog(window)
    }

    private fun deleteWindow(window: WindowConfig) {
        val updated = adapter.currentList().filterNot { it.id == window.id }
        lifecycleScope.launch { windowRepository.updateWindows(updated.mapIndexed { idx, item -> item.copy(order = idx) }) }
    }
}
