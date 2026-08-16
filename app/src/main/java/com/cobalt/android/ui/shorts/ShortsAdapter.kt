package com.cobalt.android.ui.shorts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cobalt.android.databinding.ItemShortVideoBinding

class ShortsAdapter(private val items: List<String>) : RecyclerView.Adapter<ShortsAdapter.ViewHolder>() {
    class ViewHolder(val binding: ItemShortVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.title.text = title
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShortVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
