package com.example.weblaucher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.weblaucher.BuildConfig
import com.example.weblaucher.R
import com.example.weblaucher.data.BackBehavior
import com.example.weblaucher.data.LauncherSettings
import com.example.weblaucher.data.RefreshPolicy
import com.example.weblaucher.data.SettingsDataStore
import com.example.weblaucher.data.WindowRepository
import com.example.weblaucher.databinding.ActivityHomeBinding
import com.example.weblaucher.model.WindowConfig
import com.example.weblaucher.service.KeepAliveService
import com.example.weblaucher.ui.adapter.WindowPagerAdapter
import com.example.weblaucher.ui.drawer.AppDrawerBottomSheet
import com.example.weblaucher.util.ACTION_REFRESH_ALL
import com.example.weblaucher.util.ACTION_REFRESH_CURRENT
import com.example.weblaucher.util.NetworkMonitor
import com.example.weblaucher.webview.WebViewPool
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.webkit.ValueCallback
import android.webkit.WebView

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var windowRepository: WindowRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var webViewPool: WebViewPool
    private lateinit var adapter: WindowPagerAdapter
    private lateinit var networkMonitor: NetworkMonitor
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_REFRESH_CURRENT -> {
                    val window = windows.getOrNull(binding.viewPager.currentItem) ?: return
                    webViewPool.refresh(window.id)
                }
                ACTION_REFRESH_ALL -> webViewPool.refreshAll()
            }
        }
    }

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var openMultipleLauncher: ActivityResultLauncher<Array<String>>

    private var settingsJob: Job? = null
    private var windows: List<WindowConfig> = emptyList()
    private var currentSettings: LauncherSettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        windowRepository = WindowRepository(this)
        settingsDataStore = SettingsDataStore(this)
        networkMonitor = NetworkMonitor(this, ::onNetworkChanged)

        openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val callback = fileCallback
            if (callback != null) {
                callback.onReceiveValue(uri?.let { arrayOf(it) } ?: emptyArray())
            }
            fileCallback = null
        }
        openMultipleLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val callback = fileCallback
            if (callback != null) {
                callback.onReceiveValue(uris.toTypedArray())
            }
            fileCallback = null
        }

        lifecycleScope.launch {
            windowRepository.load()
            windowRepository.windows.collectLatest { list ->
                windows = list
                if (::webViewPool.isInitialized) {
                    if (!::adapter.isInitialized) {
                        setupPager(list)
                    } else {
                        adapter.updateWindows(list)
                    }
                }
            }
        }

        settingsJob = lifecycleScope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                if (!::webViewPool.isInitialized) {
                    webViewPool = WebViewPool(
                        context = this@HomeActivity,
                        windowRepository = windowRepository,
                        settings = settings,
                        fileChooserHandler = ::launchFileChooser,
                        externalOpener = ::openExternal,
                        onRendererGone = ::onRendererGone
                    )
                    if (windows.isNotEmpty() && !::adapter.isInitialized) {
                        setupPager(windows)
                    }
                } else {
                    webViewPool.updateSettings(settings)
                }
                if (settings.foregroundService) {
                    KeepAliveService.start(this@HomeActivity)
                } else {
                    KeepAliveService.stop(this@HomeActivity)
                }
                if (BuildConfig.DEBUG) {
                    WebView.setWebContentsDebuggingEnabled(settings.debugWebView)
                }
                if (::adapter.isInitialized) {
                    val limit = settings.maxAlive.coerceAtLeast(1).coerceAtMost(windows.size)
                    binding.viewPager.offscreenPageLimit = limit
                }
            }
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupGesture()
    }

    override fun onStart() {
        super.onStart()
        networkMonitor.register()
        registerReceiver(refreshReceiver, IntentFilter().apply {
            addAction(ACTION_REFRESH_CURRENT)
            addAction(ACTION_REFRESH_ALL)
        })
    }

    override fun onStop() {
        super.onStop()
        networkMonitor.unregister()
        unregisterReceiver(refreshReceiver)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::webViewPool.isInitialized) {
            webViewPool.enforceMaxAlive()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsJob?.cancel()
        if (::webViewPool.isInitialized) {
            webViewPool.destroyAll()
        }
    }

    override fun onBackPressed() {
        val drawer = supportFragmentManager.findFragmentByTag(AppDrawerBottomSheet.TAG) as? AppDrawerBottomSheet
        if (drawer?.isVisible == true) {
            drawer.dismiss()
            return
        }
        val position = binding.viewPager.currentItem
        val window = windows.getOrNull(position)
        if (window != null && ::webViewPool.isInitialized) {
            lifecycleScope.launch {
                val settings = settingsDataStore.settingsFlow.first()
                if (settings.backBehavior == BackBehavior.DISABLED.name) {
                    Toast.makeText(this@HomeActivity, R.string.toast_no_back, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (webViewPool.canGoBack(window.id)) {
                    webViewPool.goBack(window.id)
                } else {
                    Toast.makeText(this@HomeActivity, R.string.toast_no_back, Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        super.onBackPressed()
    }

    private fun setupPager(list: List<WindowConfig>) {
        adapter = WindowPagerAdapter(list, webViewPool)
        binding.viewPager.adapter = adapter
        val limit = currentSettings?.maxAlive?.coerceAtLeast(1)?.coerceAtMost(list.size) ?: 1
        binding.viewPager.offscreenPageLimit = limit
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val window = list.getOrNull(position)
                binding.windowTitle.text = window?.name ?: ""
            }
        })
        binding.windowTitle.text = list.firstOrNull()?.name ?: ""
    }

    private fun setupGesture() {
        val threshold = dpToPx(120f)
        var startX = 0f
        var startY = 0f
        binding.homeRoot.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - startX
                    val deltaY = event.y - startY
                    if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)) {
                        return@setOnTouchListener false
                    }
                    if (deltaY < -threshold) {
                        openDrawer()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun openDrawer() {
        if (supportFragmentManager.findFragmentByTag(AppDrawerBottomSheet.TAG) != null) return
        val sheet = AppDrawerBottomSheet()
        sheet.show(supportFragmentManager, AppDrawerBottomSheet.TAG)
    }

    private fun launchFileChooser(callback: ValueCallback<Array<Uri>>, allowMultiple: Boolean, acceptTypes: Array<String>) {
        fileCallback?.onReceiveValue(emptyArray())
        fileCallback = callback
        val types = acceptTypes.filter { it.isNotBlank() }.ifEmpty { listOf("*/*") }
        if (allowMultiple) {
            openMultipleLauncher.launch(types.toTypedArray())
        } else {
            openDocumentLauncher.launch(types.toTypedArray())
        }
    }

    private fun openExternal(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun onRendererGone(windowId: String) {
        val window = windows.firstOrNull { it.id == windowId } ?: return
        webViewPool.recreate(window)
        Handler(Looper.getMainLooper()).post {
            adapter.notifyDataSetChanged()
        }
    }

    private fun onNetworkChanged(available: Boolean) {
        val message = if (available) R.string.network_available else R.string.network_lost
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (!available) return
        lifecycleScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            when (RefreshPolicy.valueOf(settings.refreshPolicy)) {
                RefreshPolicy.CURRENT -> {
                    val window = windows.getOrNull(binding.viewPager.currentItem) ?: return@launch
                    webViewPool.refresh(window.id)
                }
                RefreshPolicy.ALL -> webViewPool.refreshAll()
                RefreshPolicy.OFF -> Unit
            }
        }
    }
}
