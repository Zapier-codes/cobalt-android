package com.cobalt.android.ui

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.cobalt.android.R
import com.cobalt.android.databinding.DialogVideoPlayerBinding

/**
 * Phase 8 — tap-to-play for completed downloads.
 *
 * Reuses the same `androidx.media3`/`ExoPlayer` stack `ShortsFragment` already
 * depends on (Phase 2) rather than a second video-playback library, per
 * ARCHITECTURE.md's "Unified enhancement" rule. Unlike `ShortsFragment`'s
 * single player shared across a `ViewPager2` of many items, this dialog owns
 * exactly one player for exactly one local file, so the lifecycle is simpler:
 * built in `onViewCreated`, paused in `onPause` (backgrounding), released in
 * `onDestroyView` (screen exit) — matching Phase 8's Definition of Done and
 * the same discipline `ShortsFragment` uses.
 *
 * Plays from a `content://` MediaStore URI (`DownloadRecord.mediaStoreUriString`
 * from Phase 7's completed-download rows) — a local file, not a network
 * stream, so no HLS/DASH MIME-type branching is needed here the way
 * `ShortsFragment.playAt()` needs for remote streams.
 */
class VideoPlayerDialogFragment : DialogFragment() {

    private var _binding: DialogVideoPlayerBinding? = null
    private val binding get() = _binding!!
    private var player: ExoPlayer? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.Theme_Cobalt_FullScreenDialog)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // A plain Dialog wraps its content by default even under a fullscreen
        // theme — force it to actually fill the screen.
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogVideoPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismiss() }

        val uriString = requireArguments().getString(ARG_URI).orEmpty()
        if (uriString.isBlank()) {
            showError()
            return
        }

        val exo = ExoPlayer.Builder(requireContext()).build()
        player = exo
        binding.playerView.player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                showError()
            }
        })

        exo.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun showError() {
        binding.tvPlayerError.text = getString(R.string.player_error_unplayable)
        binding.tvPlayerError.visibility = View.VISIBLE
        binding.playerView.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
        _binding = null
    }

    companion object {
        const val TAG = "VideoPlayerDialogFragment"
        private const val ARG_URI = "arg_uri"

        fun newInstance(mediaStoreUriString: String): VideoPlayerDialogFragment =
            VideoPlayerDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_URI, mediaStoreUriString) }
            }
    }
}
