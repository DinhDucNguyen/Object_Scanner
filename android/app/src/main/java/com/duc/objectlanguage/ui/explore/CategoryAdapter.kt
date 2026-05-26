package com.duc.objectlanguage.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.R
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
            binding.categoryDescription.text = category.description ?: ""
            binding.categoryWordCount.text = binding.root.context
                .getString(R.string.explore_word_count, category.objectCount)
            binding.categoryIcon.setImageResource(iconForCategory(category.id))
            binding.root.setOnClickListener { onClick(category) }
        }
    }

    private fun iconForCategory(id: Int): Int = when (id) {
        1  -> R.drawable.electronics
        2  -> R.drawable.hoc_tap
        3  -> R.drawable.ic_kitchen
        4  -> R.drawable.ic_furniture
        5  -> R.drawable.thucpham
        6  -> R.drawable.dong_vat
        7  -> R.drawable.phuong_tien
        8  -> R.drawable.phukien
        13 -> R.drawable.person
        14 -> R.drawable.dothi
        15 -> R.drawable.sport
        16 -> R.drawable.ic_giadung
        18 -> R.drawable.place
        19 -> R.drawable.ic_bathroom
        20 -> R.drawable.ic_potted_plant
        else -> R.drawable.ic_explore
    }

    class DiffCallback : DiffUtil.ItemCallback<CategoryData>() {
        override fun areItemsTheSame(oldItem: CategoryData, newItem: CategoryData): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CategoryData, newItem: CategoryData): Boolean = oldItem == newItem
    }
}
