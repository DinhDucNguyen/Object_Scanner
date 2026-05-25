package com.duc.objectlanguage.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.databinding.ItemOnboardingSlideBinding

data class OnboardingSlide(
    val iconRes: Int,
    val titleRes: Int,
    val descRes: Int,
)

class OnboardingPagerAdapter(
    private val slides: List<OnboardingSlide>
) : RecyclerView.Adapter<OnboardingPagerAdapter.SlideViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val binding = ItemOnboardingSlideBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SlideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount() = slides.size

    class SlideViewHolder(
        private val binding: ItemOnboardingSlideBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(slide: OnboardingSlide) {
            binding.ivIcon.setImageResource(slide.iconRes)
            binding.tvTitle.setText(slide.titleRes)
            binding.tvDesc.setText(slide.descRes)
            animateEntrance()
        }

        private fun animateEntrance() {
            binding.iconContainer.apply {
                scaleX = 0f; scaleY = 0f; alpha = 0f
                animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(400).setInterpolator(OvershootInterpolator(1.4f))
                    .setStartDelay(80).start()
            }
            binding.tvTitle.apply {
                alpha = 0f; translationY = 30f
                animate().alpha(1f).translationY(0f)
                    .setDuration(320).setStartDelay(200).start()
            }
            binding.tvDesc.apply {
                alpha = 0f; translationY = 24f
                animate().alpha(1f).translationY(0f)
                    .setDuration(320).setStartDelay(300).start()
            }
        }
    }
}
