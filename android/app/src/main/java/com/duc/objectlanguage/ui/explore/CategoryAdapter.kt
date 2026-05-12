package com.duc.objectlanguage.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.data.model.CategoryData
import com.duc.objectlanguage.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onClick: (CategoryData) -> Unit
) : ListAdapter<CategoryData, CategoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: CategoryData) {
            binding.categoryName.text = category.name
            binding.categoryDescription.text = category.description ?: "Tap de xem tu vung"
            binding.categoryInitial.text = category.name.trim().take(1).uppercase()
            binding.root.setOnClickListener { onClick(category) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CategoryData>() {
        override fun areItemsTheSame(oldItem: CategoryData, newItem: CategoryData): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CategoryData, newItem: CategoryData): Boolean = oldItem == newItem
    }
}
