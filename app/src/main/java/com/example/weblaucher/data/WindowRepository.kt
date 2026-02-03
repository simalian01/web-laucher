package com.example.weblaucher.data

import android.content.Context
import com.example.weblaucher.model.WindowConfig
import com.example.weblaucher.model.WindowState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class WindowRepository(private val context: Context) {
    private val dataStore = SettingsDataStore(context)
    private val stateMap = mutableMapOf<String, WindowState>()
    private val windowsFlow = MutableStateFlow<List<WindowConfig>>(emptyList())

    val windows: Flow<List<WindowConfig>> = windowsFlow

    suspend fun load() {
        val settings = dataStore.settingsFlow.first()
        val windows = parseWindows(settings.windowsJson)
        windowsFlow.value = windows.ifEmpty { listOf(defaultWindow()) }
        if (windows.isEmpty()) {
            persistWindows(windowsFlow.value)
        }
    }

    suspend fun updateWindows(list: List<WindowConfig>) {
        windowsFlow.value = list.sortedBy { it.order }
        persistWindows(windowsFlow.value)
    }

    fun getWindowState(windowId: String): WindowState? = stateMap[windowId]

    fun updateWindowState(state: WindowState) {
        stateMap[state.windowId] = state
    }

    fun getWindowById(id: String): WindowConfig? = windowsFlow.value.firstOrNull { it.id == id }

    private suspend fun persistWindows(list: List<WindowConfig>) {
        val json = JSONArray()
        list.forEach { window ->
            val obj = JSONObject()
            obj.put("id", window.id)
            obj.put("name", window.name)
            obj.put("url", window.url)
            obj.put("order", window.order)
            json.put(obj)
        }
        dataStore.updateWindowsJson(json.toString())
    }

    private fun parseWindows(json: String): List<WindowConfig> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<WindowConfig>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    WindowConfig(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        order = obj.optInt("order", i)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun defaultWindow(): WindowConfig {
        return WindowConfig(
            id = UUID.randomUUID().toString(),
            name = context.getString(com.example.weblaucher.R.string.default_window_name),
            url = context.getString(com.example.weblaucher.R.string.default_window_url),
            order = 0
        )
    }
}
