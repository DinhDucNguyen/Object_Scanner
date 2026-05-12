package com.duc.objectlanguage.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.HistoryItem
import com.duc.objectlanguage.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.VH>(DIFF) {

    class VH(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val displayName = item.wordName
            ?: item.objectName
            ?: item.objectCode?.replace("_", " ")
            ?: "---"
        holder.binding.tvObjectCode.text = displayName.replaceFirstChar { it.uppercase() }
        holder.binding.tvDefinition.text = item.definition ?: item.categoryName ?: ""
        holder.binding.tvConfidence.text = item.confidenceScore?.let { "${(it * 100).toInt()}%" } ?: ""
        holder.binding.tvDate.text = formatDate(item.scanDate)
        Glide.with(holder.binding.ivThumbnail)
            .load(item.imageUrl)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .into(holder.binding.ivThumbnail)
        holder.binding.root.setOnClickListener { onItemClick(item) }
        holder.binding.btnDeleteHistory.setOnClickListener { onDeleteClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(a: HistoryItem, b: HistoryItem) = a.id == b.id
            override fun areContentsTheSame(a: HistoryItem, b: HistoryItem) = a == b
        }

        private fun formatDate(raw: String?): String {
            if (raw.isNullOrEmpty()) return ""
            return raw.replace("T", " ").substringBefore(".")
        }
    }
}
