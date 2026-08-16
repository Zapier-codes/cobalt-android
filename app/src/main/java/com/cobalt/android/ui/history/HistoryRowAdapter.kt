package com.cobalt.android.ui.history

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cobalt.android.databinding.ItemHistoryRowBinding
import java.text.DateFormat
import java.util.Date

/**
 * One displayable row, mapped from either a `HistoryEntity` or a
 * `LikedEntity` by `HistoryViewModel` — keeps this adapter from needing to
 * know about both entity types itself.
 */
data class HistoryRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val sourceUrl: String
)

class HistoryRowAdapter(
    private val onClick: (HistoryRow) -> Unit
) : ListAdapter<HistoryRow, HistoryRowAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemHistoryRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemHistoryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        holder.binding.tvTitle.text = row.title
        holder.binding.tvSubtitle.text = row.subtitle
        holder.binding.root.setOnClickListener { onClick(row) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<HistoryRow>() {
            override fun areItemsTheSame(a: HistoryRow, b: HistoryRow) = a.id == b.id
            override fun areContentsTheSame(a: HistoryRow, b: HistoryRow) = a == b
        }

        fun formatTimestamp(millis: Long): String =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    }
}

/** Opens [row]'s source link in the browser — same affordance as `DownloadAdapter.openFile`. */
fun openInBrowser(context: android.content.Context, row: HistoryRow) {
    if (row.sourceUrl.isBlank()) return
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(row.sourceUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: Exception) { /* no app to open this URL */ }
}
