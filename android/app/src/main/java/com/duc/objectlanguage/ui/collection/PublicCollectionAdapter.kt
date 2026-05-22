package com.duc.objectlanguage.ui.collection

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.Collection
import com.duc.objectlanguage.databinding.ItemCollectionPublicBinding

class PublicCollectionAdapter(
    private val onCollectionClick: (Collection) -> Unit
) : ListAdapter<Collection, PublicCollectionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCollectionPublicBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemCollectionPublicBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(collection: Collection) {
            val ctx = binding.root.context
            binding.collectionName.text = collection.name
            binding.ownerName.text = ctx.getString(R.string.collection_by_owner, collection.ownerName ?: "")
            binding.itemCount.text = ctx.getString(R.string.collection_item_count, collection.itemCount)
            binding.root.setOnClickListener { onCollectionClick(collection) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Collection>() {
        override fun areItemsTheSame(oldItem: Collection, newItem: Collection) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Collection, newItem: Collection) = oldItem == newItem
    }
}
