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
        // DiffUtil.ItemCallback<T>'s abstract methods are @NonNull-annotated
        // in the AndroidX Java source. The correct Kotlin override for a
        // nullable type argument (T = TranscodeProfile?) is simply the
        // plain non-null class type (TranscodeProfile) as the parameter
        // type — Kotlin/Java interop honors the Java-side @NonNull
        // annotation on T here regardless of T's own nullable upper bound
        // at the call site, and this correctly overrides the Java method.
        //
        // (Corrected this session — a previous attempt at this used
        // `TranscodeProfile & Any` "definitely non-null type" syntax,
        // which does NOT apply here: that syntax is only valid when the
        // left-hand side is an actual generic type PARAMETER with a
        // nullable bound declared in the enclosing generic scope, e.g.
        // `class Foo<T> where T : Any?  { fun bar(x: T & Any) }`.
        // TranscodeProfile is a concrete class name, not a type
        // parameter, in this anonymous `object : DiffUtil.ItemCallback<
        // TranscodeProfile?>()` — there's no T in scope for `&` to apply
        // to, hence the real compiler error.)
        //
        // Safe regardless: AsyncListDiffer never actually invokes this
        // callback with a null item — its internal wrapper short-circuits
        // null-vs-null (same) and null-vs-non-null (different) itself
        // before delegating, precisely so nullable T can mean "loading
        // placeholder" (the same pattern androidx.paging's
        // PagedListAdapter documents) without the ItemCallback ever
        // seeing a real null.
        val DIFF = object : DiffUtil.ItemCallback<TranscodeProfile?>() {
            override fun areItemsTheSame(oldItem: TranscodeProfile, newItem: TranscodeProfile) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: TranscodeProfile, newItem: TranscodeProfile) =
                oldItem == newItem
        }
    }
}
