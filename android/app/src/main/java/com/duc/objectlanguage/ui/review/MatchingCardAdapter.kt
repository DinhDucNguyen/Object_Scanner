package com.duc.objectlanguage.ui.review

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.duc.objectlanguage.R

class MatchingCardAdapter(
    private val onCardClick: (Int) -> Unit
) : ListAdapter<MatchingCard, MatchingCardAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_matching_card, parent, false)
        return ViewHolder(view, onCardClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onCardClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val card: CardView = itemView.findViewById(R.id.cardMatching)
        private val ivImage: ImageView = itemView.findViewById(R.id.ivImage)
        private val tvText: TextView = itemView.findViewById(R.id.tvText)
        private val tvCardBack: TextView = itemView.findViewById(R.id.tvCardBack)

        fun bind(matchingCard: MatchingCard) {
            if (matchingCard.isMatched) {
                // Card matched - fade out
                card.alpha = 0.3f
                card.isClickable = false
                showFront(matchingCard)
            } else if (matchingCard.isFlipped) {
                // Card flipped - show content
                card.alpha = 1.0f
                card.isClickable = false
                showFront(matchingCard)
            } else {
                // Card hidden - show back
                card.alpha = 1.0f
                card.isClickable = true
                showBack()
            }

            card.setOnClickListener {
                if (!matchingCard.isFlipped && !matchingCard.isMatched) {
                    // Flip animation
                    val flipAnimation = AnimationUtils.loadAnimation(itemView.context, R.anim.card_flip_out)
                    card.startAnimation(flipAnimation)
                    onCardClick(adapterPosition)
                }
            }
        }

        private fun showFront(matchingCard: MatchingCard) {
            tvCardBack.visibility = View.GONE

            when (matchingCard.type) {
                CardType.IMAGE -> {
                    ivImage.visibility = View.VISIBLE
                    tvText.visibility = View.GONE
                    
                    if (matchingCard.content.isNotEmpty()) {
                        Glide.with(itemView.context)
                            .load(matchingCard.content)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_placeholder)
                            .into(ivImage)
                    } else {
                        ivImage.setImageResource(R.drawable.ic_image_placeholder)
                    }
                }
                CardType.TEXT -> {
                    ivImage.visibility = View.GONE
                    tvText.visibility = View.VISIBLE
                    tvText.text = matchingCard.content
                }
            }
        }

        private fun showBack() {
            tvCardBack.visibility = View.VISIBLE
            ivImage.visibility = View.GONE
            tvText.visibility = View.GONE
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MatchingCard>() {
        override fun areItemsTheSame(oldItem: MatchingCard, newItem: MatchingCard): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MatchingCard, newItem: MatchingCard): Boolean {
            return oldItem == newItem
        }
    }
}
