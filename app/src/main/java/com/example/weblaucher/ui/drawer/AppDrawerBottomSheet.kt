package com.example.weblaucher.ui.drawer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.weblaucher.databinding.BottomSheetAppDrawerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AppDrawerBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetAppDrawerBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AppListAdapter
    private var apps: List<AppInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = AppListAdapter(emptyList(), ::launchApp)
        binding.appList.layoutManager = LinearLayoutManager(requireContext())
        binding.appList.adapter = adapter
        apps = loadApps()
        adapter.update(apps)
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty().lowercase()
                val filtered = if (query.isBlank()) {
                    apps
                } else {
                    apps.filter { it.name.lowercase().contains(query) }
                }
                adapter.update(filtered)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadApps(): List<AppInfo> {
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return activities.mapNotNull { info ->
            val launchIntent = pm.getLaunchIntentForPackage(info.activityInfo.packageName) ?: return@mapNotNull null
            AppInfo(
                name = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm),
                launchIntent = launchIntent
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun launchApp(app: AppInfo) {
        startActivity(app.launchIntent)
        dismiss()
    }

    companion object {
        const val TAG = "app_drawer"
    }
}
