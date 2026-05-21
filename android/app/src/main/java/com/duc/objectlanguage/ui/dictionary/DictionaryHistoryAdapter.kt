package com.duc.objectlanguage.ui.dictionary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.DictionaryHistoryItem
import com.duc.objectlanguage.databinding.ItemDictionaryHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DictionaryHistoryAdapter(
    private val onItemClick: (DictionaryHistoryItem) -> Unit
) : ListAdapter<DictionaryHistoryItem, DictionaryHistoryAdapter.HistoryViewHolder>(DiffCallback) {

    inner class HistoryViewHolder(private val binding: ItemDictionaryHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DictionaryHistoryItem) {
            binding.tvWord.text = item.tuTra

            if (!item.phienAm.isNullOrBlank()) {
                binding.tvPhonetic.text = item.phienAm
                binding.tvPhonetic.visibility = View.VISIBLE
            } else {
                binding.tvPhonetic.visibility = View.GONE
            }

            binding.tvTranslation.text = item.ketQuaDich ?: ""
            binding.tvTime.text = formatRelativeTime(item.lanTraCuoi)

            if (!item.imageUrl.isNullOrBlank()) {
                binding.ivObjectImage.visibility = View.VISIBLE
                binding.ivPlaceholder.visibility = View.GONE
                Glide.with(binding.root.context)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(binding.ivObjectImage)
            } else {
                binding.ivObjectImage.visibility = View.GONE
                binding.ivPlaceholder.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemDictionaryHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun formatRelativeTime(isoTime: String?): String {
        if (isoTime.isNullOrBlank()) return ""
        return try {
            val dt = parseIsoLikeTime(isoTime) ?: return ""
            val minutes = ((Date().time - dt.time) / 60000L).coerceAtLeast(0L)
            when {
                minutes < 1 -> "vừa xong"
                minutes < 60 -> "${minutes}p"
                minutes < 1440 -> "${minutes / 60}h"
                minutes < 10080 -> "${minutes / 1440}n"
                else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(dt)
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseIsoLikeTime(value: String): Date? {
        val normalized = value.take(19).replace(' ', 'T')
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(normalized)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DictionaryHistoryItem>() {
        override fun areItemsTheSame(old: DictionaryHistoryItem, new: DictionaryHistoryItem) =
            old.id == new.id

        override fun areContentsTheSame(old: DictionaryHistoryItem, new: DictionaryHistoryItem) =
            old == new
    }
}
