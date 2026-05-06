package com.duc.objectlanguage.ui.collection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.data.model.CollectionItem
import com.duc.objectlanguage.databinding.ItemCollectionWordBinding

class CollectionWordAdapter(
    private val onRemove: (translationId: Int) -> Unit
) : ListAdapter<CollectionItem, CollectionWordAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemCollectionWordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CollectionItem) {
            binding.tvWord.text = item.translation
            binding.tvDefinition.text = "${item.objectName} · ${item.category}"
            binding.tvPhonetic.visibility = View.GONE
            binding.tvPos.visibility = View.GONE
            binding.btnRemove.setOnClickListener { onRemove(item.translationId) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCollectionWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<CollectionItem>() {
        override fun areItemsTheSame(a: CollectionItem, b: CollectionItem) = a.translationId == b.translationId
        override fun areContentsTheSame(a: CollectionItem, b: CollectionItem) = a == b
    }
}
