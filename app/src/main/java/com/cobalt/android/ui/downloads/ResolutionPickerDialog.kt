package com.cobalt.android.ui.downloads

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.cobalt.android.databinding.SheetResolutionPickerBinding
import com.cobalt.android.download.DownloadService
import com.cobalt.android.link.LinkResolverRepository
import com.cobalt.android.ui.home.HomeViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Phase 6: bottom sheet listing the formats `LinkResolverRepository`
 * (Phase 4) resolved and `ResolutionCacheDao` (Phase 5) persisted.
 * Reads `HomeViewModel.resolveResult` directly (activity-scoped, shared
 * with `HomeFragment`) rather than passing formats through arguments, so
 * there's no need to make `ResolvedFormat` Parcelable for what is always a
 * same-process, same-activity hand-off.
 *
 * Confirming a format reuses `DownloadService.startHttps(...)` — the exact
 * same call `ShortsViewModel.downloadToDevice()` already drives for the
 * Shorts "save" action — so both paths converge on one download engine,
 * not two (see ARCHITECTURE.md "Unified enhancement, NOT a parallel
 * rebuild").
 */
class ResolutionPickerDialog : BottomSheetDialogFragment() {

    private var _binding: SheetResolutionPickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetResolutionPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val result = viewModel.resolveResult.value as? LinkResolverRepository.ResolveResult.Success
        if (result == null) {
            // Nothing to show (e.g. survived a process/config change without
            // the result) — don't display an empty sheet.
            dismiss()
            return
        }

        val adapter = ResolutionFormatAdapter { format ->
            DownloadService.startHttps(
                ctx = requireContext().applicationContext,
                cobaltUrl = format.url,
                filename = format.filename,
                mimeType = format.mimeType,
                cookies = "",
                userAgent = "",
                originalUrl = result.originalUrl
            )
            viewModel.clearResolveResult()
            dismiss()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        adapter.submitList(result.formats)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Covers the swipe-away-without-picking path so a stale Success
        // result doesn't silently re-show this sheet next time
        // resolveResult is observed (e.g. after a config change).
        viewModel.clearResolveResult()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ResolutionPickerDialog"
        fun newInstance() = ResolutionPickerDialog()
    }
}
