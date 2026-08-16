package com.cobalt.android.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.cobalt.android.R
import com.cobalt.android.databinding.SheetHistoryBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout

/**
 * Phase 11 — reachable from the settings sheet (a `btnHistory` entry in
 * `SettingsSheet`/`sheet_settings.xml`), not a bottom-nav tab or a top-nav
 * icon — recorded here per this phase's own Definition of Done, matching
 * the precedent Phase 7 set for `DownloadQueueSheet`'s FAB entry point.
 * A `BottomSheetDialogFragment` (like `DownloadQueueSheet`/`SettingsSheet`),
 * not a `nav_graph.xml` destination, despite the "Fragment" name the
 * original spec gave this file — `BottomSheetDialogFragment` already is a
 * `Fragment` subtype, and this keeps it consistent with how the other
 * secondary screens in this app are shown.
 */
class HistoryFragment : BottomSheetDialogFragment() {

    private var _binding: SheetHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryRowAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = HistoryRowAdapter(onClick = { row -> openInBrowser(requireContext(), row) })
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_history)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_liked)))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = updateList(tab.position == 0)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        viewModel.historyRows.observe(viewLifecycleOwner) { updateList(binding.tabLayout.selectedTabPosition == 0) }
        viewModel.likedRows.observe(viewLifecycleOwner) { updateList(binding.tabLayout.selectedTabPosition == 0) }
    }

    private fun updateList(historyTab: Boolean) {
        val list = if (historyTab) {
            viewModel.historyRows.value.orEmpty()
        } else {
            viewModel.likedRows.value.orEmpty()
        }
        adapter.submitList(list)
        binding.tvEmpty.text = getString(
            if (historyTab) R.string.no_history_yet else R.string.no_liked_yet
        )
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "HistoryFragment"
        fun newInstance() = HistoryFragment()
    }
}
