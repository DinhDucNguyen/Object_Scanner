package com.duc.objectlanguage.ui.collection

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.Collection
import com.duc.objectlanguage.databinding.ItemCollectionBinding
import java.text.SimpleDateFormat
import java.util.Locale

class CollectionAdapter(
    private val onCollectionClick: (Collection) -> Unit,
    private val onDeleteClick: (Collection) -> Unit
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
            val ctx = binding.root.context
            binding.collectionName.text = collection.name
            binding.itemCount.text = ctx.getString(R.string.collection_item_count, collection.itemCount)
            binding.createdDate.text = ctx.getString(
                R.string.collection_created_date,
                formatCollectionDate(collection.createdAt)
            )
            val progress = collectionProgressPercent(collection.itemCount)
            binding.collectionProgress.progress = progress
            binding.progressPercent.text = ctx.getString(R.string.collection_progress_percent, progress)

            binding.root.setOnClickListener { onCollectionClick(collection) }
            binding.deleteButton.setOnClickListener { onDeleteClick(collection) }
        }

        private fun formatCollectionDate(rawDate: String?): String {
            val normalized = rawDate?.take(10) ?: return "--"
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = parser.parse(normalized)
                if (date != null) formatter.format(date) else normalized
            } catch (_: Exception) {
                normalized
            }
        }

        private fun collectionProgressPercent(itemCount: Int): Int {
            return itemCount.coerceIn(0, 4) * 25
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
