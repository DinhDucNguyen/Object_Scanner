package com.duc.objectlanguage.ui.collection

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.data.model.Collection
import com.duc.objectlanguage.databinding.ItemCollectionBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView adapter for collection list
 */
class CollectionAdapter(
    private val onCollectionClick: (Collection) -> Unit,
    private val onDeleteClick: (Collection) -> Unit,
    private val onInsightsClick: (Collection) -> Unit
) : ListAdapter<Collection, CollectionAdapter.ViewHolder>(CollectionDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCollectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: ItemCollectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(collection: Collection) {
            binding.collectionName.text = collection.name
            binding.itemCount.text = "${collection.itemCount} items"
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.createdDate.text = "Created ${dateFormat.format(collection.createdAt)}"
            
            // Click handlers
            binding.root.setOnClickListener {
                onCollectionClick(collection)
            }
            
            binding.deleteButton.setOnClickListener {
                onDeleteClick(collection)
            }
            
            binding.insightsButton.setOnClickListener {
                onInsightsClick(collection)
            }
        }
    }
    
    class CollectionDiffCallback : DiffUtil.ItemCallback<Collection>() {
        override fun areItemsTheSame(oldItem: Collection, newItem: Collection): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Collection, newItem: Collection): Boolean {
            return oldItem == newItem
        }
    }
}
