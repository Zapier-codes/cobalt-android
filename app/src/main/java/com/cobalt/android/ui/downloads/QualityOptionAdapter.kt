package com.cobalt.android.ui.downloads

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cobalt.android.databinding.ItemQualityOptionBinding
import com.cobalt.android.transcode.TranscodeProfile

/**
 * Phase 20: renders [TranscodeProfile.ALL_VIDEO] or [TranscodeProfile.ALL_AUDIO]
 * (whichever [QualitySelectionSheet]'s toggle currently selects) plus a
 * leading `null` entry meaning "Original / no conversion" — a real, always-
 * available choice, not a placeholder row, since skipping FFmpeg entirely
 * and downloading the source format as-is is a fully supported, first-
 * class outcome of this sheet.
 */
class QualityOptionAdapter(
    private val onPick: (TranscodeProfile?) -> Unit
) : ListAdapter<TranscodeProfile?, QualityOptionAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemQualityOptionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemQualityOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val profile = getItem(position)
        with(holder.binding) {
            if (profile == null) {
                tvLabel.text = root.context.getString(com.cobalt.android.R.string.quality_option_original)
                tvExtension.text = ""
            } else {
                tvLabel.text = profile.label
                tvExtension.text = ".${profile.extension}"
            }
            root.setOnClickListener { onPick(profile) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TranscodeProfile?>() {
            override fun areItemsTheSame(a: TranscodeProfile?, b: TranscodeProfile?) =
                (a?.id ?: "original") == (b?.id ?: "original")
            override fun areContentsTheSame(a: TranscodeProfile?, b: TranscodeProfile?) = a == b
        }
    }
}
