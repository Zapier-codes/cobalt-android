package com.cobalt.android.ui.downloads

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cobalt.android.databinding.ItemResolutionFormatBinding
import com.cobalt.android.link.LinkResolverRepository.ResolvedFormat

class ResolutionFormatAdapter(
    private val onPick: (ResolvedFormat) -> Unit
) : ListAdapter<ResolvedFormat, ResolutionFormatAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemResolutionFormatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemResolutionFormatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val format = getItem(position)
        with(holder.binding) {
            tvLabel.text = format.label
            tvFilename.text = format.filename
            root.setOnClickListener { onPick(format) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ResolvedFormat>() {
            override fun areItemsTheSame(a: ResolvedFormat, b: ResolvedFormat) = a.url == b.url
            override fun areContentsTheSame(a: ResolvedFormat, b: ResolvedFormat) = a == b
        }
    }
}
