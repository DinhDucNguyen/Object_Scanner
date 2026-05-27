package com.duc.objectlanguage.ui.collection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.data.model.CollectionItem
import com.duc.objectlanguage.databinding.ItemCollectionWordBinding
import java.util.Locale

class CollectionWordAdapter(
    private val onRemove: (translationId: Int) -> Unit,
    private val onOpenDetail: (item: CollectionItem) -> Unit
) : ListAdapter<CollectionItem, CollectionWordAdapter.ViewHolder>(DiffCallback()) {

    private var canEdit: Boolean = true

    fun setCanEdit(value: Boolean) {
        if (canEdit == value) return
        canEdit = value
        notifyItemRangeChanged(0, itemCount)
    }

    inner class ViewHolder(private val binding: ItemCollectionWordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CollectionItem) {
            binding.tvWord.text = item.translation
            binding.tvDefinition.visibility = View.GONE
            binding.tvCategory.text = item.category.uppercase(Locale.getDefault())
            binding.btnRemove.visibility = if (canEdit) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onOpenDetail(item) }
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
