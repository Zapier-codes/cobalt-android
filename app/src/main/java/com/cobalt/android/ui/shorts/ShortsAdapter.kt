package com.cobalt.android.ui.shorts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cobalt.android.databinding.ItemShortVideoBinding
import com.cobalt.android.shorts.model.ShortItem

/**
 * One page per [ShortItem] inside the vertical `ViewPager2`. Video playback
 * itself is NOT owned per-item — a single shared `ExoPlayer` (owned by
 * `ShortsFragment`) is attached to whichever ViewHolder's `PlayerView` is
 * currently the active page, matching the standard TikTok/Shorts-style feed
 * pattern of one live player at a time rather than one per row.
 */
class ShortsAdapter(
    private val onLikeClick: (ShortItem) -> Unit,
    private val onSaveClick: (ShortItem) -> Unit,
    private val onShareClick: (ShortItem) -> Unit
) : ListAdapter<ShortItem, ShortsAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(val binding: ItemShortVideoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShortVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.tvCaption.text = "${item.title}  •  ${item.authorName}"
        holder.binding.ivLike.setImageResource(
            if (item.isLiked) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )
        holder.binding.ivLike.setOnClickListener { onLikeClick(item) }
        holder.binding.ivSave.setOnClickListener { onSaveClick(item) }
        holder.binding.ivShare.setOnClickListener { onShareClick(item) }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ShortItem>() {
            override fun areItemsTheSame(oldItem: ShortItem, newItem: ShortItem) =
                oldItem.videoId == newItem.videoId

            override fun areContentsTheSame(oldItem: ShortItem, newItem: ShortItem) =
                oldItem == newItem
        }
    }
}
