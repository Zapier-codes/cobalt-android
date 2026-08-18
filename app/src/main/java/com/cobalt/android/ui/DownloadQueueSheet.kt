package com.cobalt.android.ui

import android.animation.ValueAnimator
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
import com.cobalt.android.ui.widget.SkeletonPulse
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout

class DownloadQueueSheet : BottomSheetDialogFragment() {

    private var _binding: SheetDownloadQueueBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DownloadQueueViewModel by activityViewModels()
    private lateinit var adapter: DownloadAdapter

    // Phase 17: Room's query LiveData has no value until its first
    // background-thread emission, so there's a real (if brief) gap on
    // sheet open before allDownloads/activeDownloads say anything —
    // skeletonDownloads covers exactly that gap, once, then never again
    // for this sheet instance's lifetime.
    private var hasLoadedOnce = false
    private var skeletonAnimator: ValueAnimator? = null

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

        // Phase 17: skeletonDownloads starts visible per its XML default;
        // this just starts the pulse animating it. Cancelled for good the
        // moment the first real emission arrives — see updateList().
        skeletonAnimator = SkeletonPulse.start(binding.skeletonDownloads)

        // Real data arriving (not a tab switch — see markLoadedOnce()'s
        // own comment for why that distinction matters) is what retires
        // the skeleton, so it's marked here, not inside updateList()
        // itself (which the tab-selection listener above also calls).
        viewModel.allDownloads.observe(viewLifecycleOwner) {
            markLoadedOnce()
            updateList(binding.tabLayout.selectedTabPosition == 0)
        }
        viewModel.activeDownloads.observe(viewLifecycleOwner) {
            markLoadedOnce()
            updateList(binding.tabLayout.selectedTabPosition == 0)
        }
    }

    /** Phase 17: retires skeletonDownloads on the first *real* emission.
     * Deliberately not folded into updateList() — that's also invoked by
     * the tab-switch listener above, and a tab switch before either
     * LiveData has emitted yet would otherwise read a null-backed empty
     * list and retire the skeleton prematurely, before any real data
     * actually arrived. */
    private fun markLoadedOnce() {
        if (hasLoadedOnce) return
        hasLoadedOnce = true
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        binding.skeletonDownloads.visibility = View.GONE
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
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        _binding = null
    }

    companion object {
        const val TAG = "DownloadQueueSheet"
        fun newInstance() = DownloadQueueSheet()
    }
}
