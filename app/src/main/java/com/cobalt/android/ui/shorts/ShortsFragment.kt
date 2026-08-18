package com.cobalt.android.ui.shorts

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.cobalt.android.databinding.FragmentShortsBinding
import com.cobalt.android.shorts.model.ShortItem
import com.cobalt.android.shorts.model.StreamKind
import com.cobalt.android.ui.widget.SkeletonPulse

class ShortsFragment : Fragment() {

    private var _binding: FragmentShortsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShortsViewModel by viewModels()
    private lateinit var adapter: ShortsAdapter
    private var player: ExoPlayer? = null
    private var currentlyBoundPosition = RecyclerView.NO_POSITION

    // Phase 17: see HomeFragment's identically-named field — same
    // cancel-not-just-hide lifecycle contract from SkeletonPulse.
    private var skeletonAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShortsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ShortsAdapter(
            onLikeClick = { viewModel.toggleLike(it) },
            onSaveClick = { viewModel.downloadToDevice(it) },
            onShareClick = { shareShort(it) }
        )
        binding.vpShorts.adapter = adapter
        binding.vpShorts.offscreenPageLimit = 1

        binding.vpShorts.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                playAt(position)
                // Start prefetching the next page a couple items before the end
                // of what's currently loaded, so scrolling never visibly stalls.
                val list = adapter.currentList
                if (position >= list.size - 3) viewModel.loadMore()
            }
        })

        viewModel.shorts.observe(viewLifecycleOwner) { list ->
            val previousPosition = binding.vpShorts.currentItem
            adapter.submitList(list) {
                if (list.isNotEmpty() && currentlyBoundPosition == RecyclerView.NO_POSITION) {
                    playAt(previousPosition.coerceIn(0, list.size - 1))
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.skeletonShorts.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading) {
                skeletonAnimator?.cancel()
                skeletonAnimator = SkeletonPulse.start(binding.skeletonShorts)
            } else {
                skeletonAnimator?.cancel()
                skeletonAnimator = null
            }
        }

        // Phase 16: DoD-1 — a cache-fallback page must not silently look
        // identical to a live one.
        viewModel.isOffline.observe(viewLifecycleOwner) { offline ->
            binding.tvOfflineBanner.visibility = if (offline) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        ensurePlayer()
        // Phase 16: previously called the full playAt(currentlyBoundPosition)
        // here, which re-did exo.stop()/clearMediaItems()/setMediaItem()/
        // prepare() — a needless re-buffer of a video that was simply
        // paused, not released — and, worse, called viewModel.recordWatch()
        // again for an item the user didn't newly start watching, writing a
        // duplicate History row every time the app was backgrounded and
        // resumed. currentlyBoundPosition is only ever non-NO_POSITION when
        // `player` already has that item loaded (it's set in playAt() right
        // after a successful setMediaItem(), and reset to NO_POSITION
        // exactly when `player` is released in onDestroyView()) — so
        // resuming is always correct here; a fresh playAt() is unnecessary.
        if (currentlyBoundPosition != RecyclerView.NO_POSITION) {
            player?.playWhenReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    private fun ensurePlayer() {
        if (player == null) player = ExoPlayer.Builder(requireContext()).build()
    }

    /**
     * Attaches the single shared player to the PlayerView at [position] and
     * starts it. Right after a fresh `submitList`, the target page's
     * ViewHolder may not have been laid out yet — [retryIfMissing] allows one
     * deferred retry via `post()` instead of silently doing nothing.
     */
    private fun playAt(position: Int, retryIfMissing: Boolean = true) {
        val item = adapter.currentList.getOrNull(position) ?: return
        ensurePlayer()
        val exo = player ?: return

        val recyclerView = binding.vpShorts.getChildAt(0) as? RecyclerView ?: return
        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? ShortsAdapter.ViewHolder
        if (holder == null) {
            if (retryIfMissing) recyclerView.post { playAt(position, retryIfMissing = false) }
            return
        }

        // Phase 16: defense-in-depth against the same duplicate-History-
        // write class of bug the onResume() fix above addresses — if
        // something ever calls playAt() again for the position that's
        // already bound (onResume()'s own case is now handled without
        // reaching here at all, see above), don't re-log a watch for a
        // video the user didn't newly start.
        val isNewItem = position != currentlyBoundPosition

        exo.stop()
        exo.clearMediaItems()
        holder.binding.playerView.player = exo

        val mediaItem = when (item.streamKind) {
            StreamKind.HLS -> MediaItem.Builder()
                .setUri(item.streamUrl)
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                .build()
            StreamKind.DASH -> MediaItem.Builder()
                .setUri(item.streamUrl)
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                .build()
            StreamKind.PROGRESSIVE -> MediaItem.fromUri(item.streamUrl)
        }

        exo.setMediaItem(mediaItem)
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.prepare()
        exo.playWhenReady = true
        currentlyBoundPosition = position
        if (isNewItem) viewModel.recordWatch(item)
    }

    private fun shareShort(item: ShortItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, item.watchUrl)
        }
        startActivity(Intent.createChooser(intent, item.title))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
        currentlyBoundPosition = RecyclerView.NO_POSITION
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        _binding = null
    }
}
