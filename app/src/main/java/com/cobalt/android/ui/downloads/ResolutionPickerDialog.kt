package com.cobalt.android.ui.downloads

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.cobalt.android.databinding.SheetResolutionPickerBinding
import com.cobalt.android.link.LinkResolverRepository
import com.cobalt.android.ui.home.HomeViewModel
import com.cobalt.android.util.SettingsRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Phase 6: bottom sheet listing the formats `LinkResolverRepository`
 * (Phase 4) resolved and `ResolutionCacheDao` (Phase 5) persisted.
 * Reads `HomeViewModel.resolveResult` directly (activity-scoped, shared
 * with `HomeFragment`) rather than passing formats through arguments, so
 * there's no need to make `ResolvedFormat` Parcelable for what is always a
 * same-process, same-activity hand-off.
 *
 * Confirming a format hands off to `QualitySelectionSheet` (Phase 15),
 * which is what actually calls `DownloadService.startHttps(...)` — the
 * exact same call `ShortsViewModel.downloadToDevice()` already drives for
 * the Shorts "save" action — so both paths still converge on one download
 * engine, not two (see ARCHITECTURE.md "Unified enhancement, NOT a
 * parallel rebuild"). Shorts' save action (`ShortsViewModel.downloadToDevice`)
 * calls `startHttps` directly and skips the quality sheet entirely — a
 * deliberate scope boundary for this phase, not an oversight; wiring
 * FFmpeg quality selection into the Shorts feed is future work.
 *
 * Phase 14: honors `SettingsRepository.defaultDownloadFormat`. Since this
 * app's `ResolvedFormat.label` is a format *type* ("video"/"audio"/"photo
 * N"), not a quality ladder (see `SettingsRepository.DownloadFormatPreference`
 * KDoc), "pre-select" means: if the user has a non-ASK preference and
 * exactly one resolved format matches that type, skip the sheet entirely
 * and download it immediately — matching how a real "default format"
 * setting behaves in comparable downloader apps, not just a highlighted
 * list item the user still has to tap. If the preference doesn't
 * unambiguously match (zero or multiple candidates — e.g. a link that only
 * ever resolves to a photo), the full list is shown as before, sorted so
 * the preferred type (if present at all) sorts first.
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

        val preference = SettingsRepository(requireContext()).defaultDownloadFormat
        val preferredLabel = when (preference) {
            SettingsRepository.DownloadFormatPreference.VIDEO -> "video"
            SettingsRepository.DownloadFormatPreference.AUDIO -> "audio"
            SettingsRepository.DownloadFormatPreference.ASK -> null
        }

        if (preferredLabel != null) {
            val matches = result.formats.filter { it.label.equals(preferredLabel, ignoreCase = true) }
            if (matches.size == 1) {
                // Exactly one candidate for the preferred type — this is
                // what "default format" means: skip the sheet, download
                // now. No tap required.
                downloadAndClose(matches.first(), result.originalUrl)
                return
            }
            // Zero or multiple matches (e.g. a photo-only resolve, or a
            // future format that resolves multiple "video" entries) —
            // ambiguous, fall through to a normal list, just reordered.
        }

        val orderedFormats = if (preferredLabel != null) {
            result.formats.sortedByDescending { it.label.equals(preferredLabel, ignoreCase = true) }
        } else {
            result.formats
        }

        val adapter = ResolutionFormatAdapter { format -> downloadAndClose(format, result.originalUrl) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        adapter.submitList(orderedFormats)
    }

    // Phase 15: this no longer starts the download itself — it hands the
    // chosen source format to QualitySelectionSheet, which is what
    // actually calls DownloadService.startHttps() (with or without a
    // TranscodeProfile attached) once the user also picks a target
    // quality/format. Name kept as-is even though its job changed, since
    // every call site below is still "the user confirmed a format, close
    // this dialog" — same trigger, new next step.
    private fun downloadAndClose(format: LinkResolverRepository.ResolvedFormat, originalUrl: String) {
        // parentFragmentManager, not childFragmentManager: this dialog is
        // about to dismiss() itself below, which tears down its own
        // childFragmentManager (and anything shown through it) along with
        // it. Showing the next sheet as a sibling through the *parent's*
        // manager (the same one HomeFragment used to show this dialog in
        // the first place) is what lets it survive this dialog's own
        // dismissal.
        QualitySelectionSheet.newInstance(
            url = format.url,
            filename = format.filename,
            mimeType = format.mimeType,
            label = format.label,
            originalUrl = originalUrl
        ).show(parentFragmentManager, QualitySelectionSheet.TAG)
        viewModel.clearResolveResult()
        dismiss()
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
