package com.cobalt.android.ui.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.cobalt.android.databinding.SheetQualitySelectionBinding
import com.cobalt.android.download.DownloadService
import com.cobalt.android.transcode.TranscodeProfile
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Phase 20: the quality/format picker FFmpeg targets, shown after
 * [ResolutionPickerDialog] hands off a chosen source [ ][com.cobalt.android.link.LinkResolverRepository.ResolvedFormat]. Lets the
 * user pick literally any tier in [TranscodeProfile.ALL_VIDEO] /
 * [TranscodeProfile.ALL_AUDIO] — nothing here is capped to a "preview" or
 * "free tier" subset; every profile the ladder defines is reachable and
 * fully wired to a real ffmpeg command (see `FfmpegTranscoder`).
 *
 * Receives the chosen source format as plain string arguments (url,
 * filename, mimeType, label, originalUrl) rather than the
 * non-Parcelable `ResolvedFormat` itself or a `HomeViewModel` reference —
 * unlike `ResolutionPickerDialog`, this sheet doesn't need the rest of
 * `HomeViewModel`'s resolve-result list, only the one format the user
 * already picked, so a plain `Bundle` of primitives is simpler than either
 * making `ResolvedFormat` Parcelable or coupling this sheet to the Home
 * screen's ViewModel.
 */
class QualitySelectionSheet : BottomSheetDialogFragment() {

    private var _binding: SheetQualitySelectionBinding? = null
    private val binding get() = _binding!!

    private val sourceUrl by lazy { requireArguments().getString(ARG_URL, "") }
    private val sourceFilename by lazy { requireArguments().getString(ARG_FILENAME, "download") }
    private val sourceMimeType by lazy { requireArguments().getString(ARG_MIME_TYPE, "application/octet-stream") }
    private val sourceLabel by lazy { requireArguments().getString(ARG_LABEL, "") }
    private val originalUrl by lazy { requireArguments().getString(ARG_ORIGINAL_URL, "") }

    private val adapter = QualityOptionAdapter { profile -> startDownload(profile) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetQualitySelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // Default the toggle to whichever kind matches the source format —
        // a source labeled "audio" almost always means the user wants an
        // audio-quality ladder, not to be shown video tiers first (ffmpeg
        // can't produce video from an audio-only source anyway, so video
        // tiers would just fail if picked against one). Both toggle
        // options stay tappable regardless; this only picks the sheet's
        // initial state.
        val startOnAudio = sourceLabel.equals("audio", ignoreCase = true)
        binding.toggleKind.check(if (startOnAudio) binding.btnKindAudio.id else binding.btnKindVideo.id)
        showList(video = !startOnAudio)

        binding.toggleKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) showList(video = checkedId == binding.btnKindVideo.id)
        }
    }

    private fun showList(video: Boolean) {
        val items: List<TranscodeProfile?> =
            listOf(null) + if (video) TranscodeProfile.ALL_VIDEO else TranscodeProfile.ALL_AUDIO
        adapter.submitList(items)
    }

    private fun startDownload(profile: TranscodeProfile?) {
        DownloadService.startHttps(
            ctx = requireContext().applicationContext,
            cobaltUrl = sourceUrl,
            filename = sourceFilename,
            mimeType = sourceMimeType,
            cookies = "",
            userAgent = "",
            originalUrl = originalUrl,
            transcodeProfile = profile
        )
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "QualitySelectionSheet"
        private const val ARG_URL = "url"
        private const val ARG_FILENAME = "filename"
        private const val ARG_MIME_TYPE = "mimeType"
        private const val ARG_LABEL = "label"
        private const val ARG_ORIGINAL_URL = "originalUrl"

        fun newInstance(
            url: String, filename: String, mimeType: String, label: String, originalUrl: String
        ) = QualitySelectionSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_URL, url)
                putString(ARG_FILENAME, filename)
                putString(ARG_MIME_TYPE, mimeType)
                putString(ARG_LABEL, label)
                putString(ARG_ORIGINAL_URL, originalUrl)
            }
        }
    }
}
