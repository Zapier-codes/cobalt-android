package com.cobalt.android.ui

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cobalt.android.databinding.ItemDownloadBinding
import com.cobalt.android.download.DownloadRecord
import com.cobalt.android.download.DownloadStatus

class DownloadAdapter(
    private val onRetry: (DownloadRecord) -> Unit,
    private val onCancel: (DownloadRecord) -> Unit,
    private val onPlay: (DownloadRecord) -> Unit
) : ListAdapter<DownloadRecord, DownloadAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val record = getItem(position)
        with(holder.binding) {
            tvFilename.text = record.filename.ifBlank { "downloading…" } +
                // Phase 20: label a transcode-output row with the quality
                // it targets, so it reads distinctly from the raw
                // download it was produced from in the same queue list.
                if (record.transcodeProfileLabel.isNotBlank()) " · ${record.transcodeProfileLabel}" else ""

            // Reset all action buttons first
            btnOpen.visibility = View.GONE
            btnRetry.visibility = View.GONE
            btnCancel.visibility = View.GONE
            progressBar.visibility = View.GONE

            when (record.status) {
                DownloadStatus.QUEUED -> {
                    tvStatus.text = "queued"
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = true
                    btnCancel.visibility = View.VISIBLE
                    btnCancel.setOnClickListener { onCancel(record) }
                }
                DownloadStatus.DOWNLOADING -> {
                    val pct = if (record.totalBytes > 0)
                        (record.bytesDownloaded * 100 / record.totalBytes).toInt() else 0
                    tvStatus.text = if (record.totalBytes > 0) {
                        val dl = record.bytesDownloaded / 1_048_576.0
                        val total = record.totalBytes / 1_048_576.0
                        "%.1f / %.1f MB".format(dl, total)
                    } else {
                        "%.1f MB".format(record.bytesDownloaded / 1_048_576.0)
                    }
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = record.totalBytes <= 0
                    progressBar.progress = pct
                    btnCancel.visibility = View.VISIBLE
                    btnCancel.setOnClickListener { onCancel(record) }
                }
                DownloadStatus.CONVERTING -> {
                    val pct = if (record.totalBytes > 0)
                        (record.bytesDownloaded * 100 / record.totalBytes).toInt() else 0
                    tvStatus.text = if (record.totalBytes > 0) "converting… $pct%" else "converting…"
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = record.totalBytes <= 0
                    progressBar.progress = pct
                    // No cancel button: FFmpegKit sessions aren't wired to
                    // DownloadQueueSheet's onCancel path this phase (that
                    // path currently only knows how to stop DownloadService
                    // HTTP transfers) — cancelling an in-flight transcode
                    // is left for a later phase rather than shown as a
                    // button that silently does nothing.
                }
                DownloadStatus.COMPLETE -> {
                    val mb = if (record.totalBytes > 0) "%.1f MB · ".format(record.totalBytes / 1_048_576.0) else ""
                    tvStatus.text = "${mb}saved"
                    btnOpen.visibility = View.VISIBLE
                    btnOpen.setOnClickListener {
                        if (record.mimeType.startsWith("video/") && record.mediaStoreUriString.isNotBlank()) {
                            // Phase 8: play locally via the in-app player instead of
                            // handing off to whatever system app claims the MIME type.
                            onPlay(record)
                        } else {
                            openFile(holder.binding.root.context, record)
                        }
                    }
                }
                DownloadStatus.FAILED -> {
                    tvStatus.text = "failed"
                    btnRetry.visibility = View.VISIBLE
                    btnRetry.setOnClickListener { onRetry(record) }
                }
                DownloadStatus.FAILED_NETWORK -> {
                    tvStatus.text = "network error"
                    btnRetry.visibility = View.VISIBLE
                    btnRetry.setOnClickListener { onRetry(record) }
                }
            }
        }
    }

    private fun openFile(context: android.content.Context, record: DownloadRecord) {
        val uriStr = record.mediaStoreUriString
        if (uriStr.isBlank()) return
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(uriStr), record.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) { /* no app to open this type */ }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DownloadRecord>() {
            override fun areItemsTheSame(a: DownloadRecord, b: DownloadRecord) = a.id == b.id
            override fun areContentsTheSame(a: DownloadRecord, b: DownloadRecord) = a == b
        }
    }
}
