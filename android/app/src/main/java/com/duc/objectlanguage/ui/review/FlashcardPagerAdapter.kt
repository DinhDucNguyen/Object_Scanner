package com.duc.objectlanguage.ui.review

import android.animation.AnimatorInflater
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.ReviewCardResponse
import com.duc.objectlanguage.databinding.ItemFlashcardBinding

class FlashcardPagerAdapter(
    private val onCardFlipped: (Int) -> Unit
) : ListAdapter<ReviewCardResponse, FlashcardPagerAdapter.FlashcardViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlashcardViewHolder {
        val binding = ItemFlashcardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FlashcardViewHolder(binding, onCardFlipped)
    }

    override fun onBindViewHolder(holder: FlashcardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FlashcardViewHolder(
        private val binding: ItemFlashcardBinding,
        private val onFlipped: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isFlipped = false
        private var currentCard: ReviewCardResponse? = null

        init {
            binding.cardContainer.setOnClickListener {
                flipCard()
            }
        }

        fun bind(card: ReviewCardResponse) {
            currentCard = card
            isFlipped = false

            // Show front (English word)
            binding.cardFront.visibility = View.VISIBLE
            binding.cardBack.visibility = View.GONE

            // Front side
            binding.tvWordFront.text = card.wordName.replaceFirstChar { it.uppercase() }
            binding.tvLanguageFront.text = card.languageName

            // Back side
            binding.tvWordBack.text = card.wordName.replaceFirstChar { it.uppercase() }
            binding.tvPhonetic.text = card.phonetic ?: ""
            binding.tvPhonetic.visibility = if (card.phonetic.isNullOrEmpty()) View.GONE else View.VISIBLE

            binding.tvDefinition.text = card.definition ?: card.objectCode.replace("_", " ").replaceFirstChar { it.uppercase() }

            binding.tvExample.text = card.exampleSentence ?: ""
            binding.tvExample.visibility = if (card.exampleSentence.isNullOrEmpty()) View.GONE else View.VISIBLE

            // SM-2 Stats
            binding.tvStats.text = "EF: %.2f | Interval: %d days | Rep: %d".format(
                card.easinessFactor,
                card.interval,
                card.repetitions
            )
        }

        private fun flipCard() {
            val context = binding.root.context

            // Load flip animation
            val flipOut = AnimatorInflater.loadAnimator(
                context,
                R.animator.card_flip_out
            )
            val flipIn = AnimatorInflater.loadAnimator(
                context,
                R.animator.card_flip_in
            )

            if (!isFlipped) {
                // Flip to back
                flipOut.setTarget(binding.cardFront)
                flipIn.setTarget(binding.cardBack)

                flipOut.start()
                binding.cardFront.postDelayed({
                    binding.cardFront.visibility = View.GONE
                    binding.cardBack.visibility = View.VISIBLE
                    flipIn.start()
                }, 150)

                isFlipped = true
            } else {
                // Flip to front
                flipOut.setTarget(binding.cardBack)
                flipIn.setTarget(binding.cardFront)

                flipOut.start()
                binding.cardBack.postDelayed({
                    binding.cardBack.visibility = View.GONE
                    binding.cardFront.visibility = View.VISIBLE
                    flipIn.start()
                }, 150)

                isFlipped = false
            }

            onFlipped(adapterPosition)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ReviewCardResponse>() {
        override fun areItemsTheSame(
            oldItem: ReviewCardResponse,
            newItem: ReviewCardResponse
        ): Boolean {
            return oldItem.progressId == newItem.progressId
        }

        override fun areContentsTheSame(
            oldItem: ReviewCardResponse,
            newItem: ReviewCardResponse
        ): Boolean {
            return oldItem == newItem
        }
    }
}
