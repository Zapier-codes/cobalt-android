package com.cobalt.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.cobalt.android.R
import com.cobalt.android.databinding.SheetDownloadQueueBinding
import com.cobalt.android.download.DownloadRecord
import com.cobalt.android.download.DownloadStatus
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout

class DownloadQueueSheet : BottomSheetDialogFragment() {

    private var _binding: SheetDownloadQueueBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DownloadQueueViewModel by activityViewModels()
    private lateinit var adapter: DownloadAdapter

    var onRetry: ((DownloadRecord) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetDownloadQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = DownloadAdapter(
            onRetry = { record -> onRetry?.invoke(record) },
            onCancel = { /* future: cancel in-progress download */ },
            onPlay = { record ->
                VideoPlayerDialogFragment.newInstance(record.mediaStoreUriString)
                    .show(parentFragmentManager, VideoPlayerDialogFragment.TAG)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_active)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_history)))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = updateList(tab.position == 0)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        viewModel.allDownloads.observe(viewLifecycleOwner) { updateList(binding.tabLayout.selectedTabPosition == 0) }
        viewModel.activeDownloads.observe(viewLifecycleOwner) { updateList(binding.tabLayout.selectedTabPosition == 0) }
    }

    private fun updateList(activeTab: Boolean) {
        val list: List<DownloadRecord> = if (activeTab) {
            viewModel.activeDownloads.value ?: emptyList()
        } else {
            viewModel.allDownloads.value?.filter {
                it.status == DownloadStatus.COMPLETE || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.FAILED_NETWORK
            } ?: emptyList()
        }
        adapter.submitList(list)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DownloadQueueSheet"
        fun newInstance() = DownloadQueueSheet()
    }
}
