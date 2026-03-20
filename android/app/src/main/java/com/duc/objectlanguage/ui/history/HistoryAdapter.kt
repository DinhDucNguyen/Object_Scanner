package com.duc.objectlanguage.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.data.model.HistoryItem
import com.duc.objectlanguage.databinding.ItemHistoryBinding

class HistoryAdapter : ListAdapter<HistoryItem, HistoryAdapter.VH>(DIFF) {

    class VH(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvObjectCode.text = item.objectCode?.replaceFirstChar { it.uppercase() } ?: "---"
        holder.binding.tvDate.text = item.scanDate ?: ""
        holder.binding.tvConfidence.text = item.confidenceScore?.let { "${(it * 100).toInt()}%" } ?: ""
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(a: HistoryItem, b: HistoryItem) = a.id == b.id
            override fun areContentsTheSame(a: HistoryItem, b: HistoryItem) = a == b
        }
    }
}
