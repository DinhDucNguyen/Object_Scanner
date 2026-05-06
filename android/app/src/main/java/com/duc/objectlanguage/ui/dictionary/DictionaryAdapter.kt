package com.duc.objectlanguage.ui.dictionary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.data.model.DictionaryMeaning
import com.duc.objectlanguage.databinding.ItemDictionaryMeaningBinding

class DictionaryAdapter : ListAdapter<DictionaryMeaning, DictionaryAdapter.MeaningViewHolder>(DiffCallback) {

    class MeaningViewHolder(private val binding: ItemDictionaryMeaningBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(meaning: DictionaryMeaning) {
            binding.tvPartOfSpeech.text = meaning.partOfSpeech
            binding.tvDefinition.text = meaning.definition
            
            if (meaning.example != null) {
                binding.tvExample.visibility = View.VISIBLE
                binding.tvExample.text = "\"${meaning.example}\""
            } else {
                binding.tvExample.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeaningViewHolder {
        val binding = ItemDictionaryMeaningBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MeaningViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MeaningViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DictionaryMeaning>() {
        override fun areItemsTheSame(oldItem: DictionaryMeaning, newItem: DictionaryMeaning): Boolean {
            return oldItem.definition == newItem.definition
        }

        override fun areContentsTheSame(oldItem: DictionaryMeaning, newItem: DictionaryMeaning): Boolean {
            return oldItem == newItem
        }
    }
}
