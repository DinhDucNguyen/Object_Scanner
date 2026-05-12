package com.duc.objectlanguage.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.ObjectData
import com.duc.objectlanguage.databinding.ItemExploreObjectBinding

class ObjectAdapter(
    private val onClick: (ObjectData) -> Unit
) : ListAdapter<ObjectData, ObjectAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExploreObjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemExploreObjectBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ObjectData) {
            binding.wordName.text = item.wordName ?: item.objectCode.replace("_", " ")
            binding.objectCode.text = item.objectCode
            binding.definition.text = item.definition ?: "Chua co nghia hien thi"
            binding.phonetic.text = item.phonetic ?: ""

            if (item.imageUrl.isNullOrBlank()) {
                binding.objectImage.setImageResource(R.drawable.ic_image_placeholder)
            } else {
                Glide.with(binding.objectImage)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(binding.objectImage)
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ObjectData>() {
        override fun areItemsTheSame(oldItem: ObjectData, newItem: ObjectData): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ObjectData, newItem: ObjectData): Boolean = oldItem == newItem
    }
}
