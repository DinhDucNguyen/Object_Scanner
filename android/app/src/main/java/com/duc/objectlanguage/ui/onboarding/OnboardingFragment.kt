package com.duc.objectlanguage.ui.onboarding

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentOnboardingBinding
import com.duc.objectlanguage.ui.common.addScaleFeedback

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val slides = listOf(
        OnboardingSlide(R.drawable.ic_camera, R.string.onboarding_slide1_title, R.string.onboarding_slide1_desc),
        OnboardingSlide(R.drawable.ic_book,   R.string.onboarding_slide2_title, R.string.onboarding_slide2_desc),
        OnboardingSlide(R.drawable.ic_star,   R.string.onboarding_slide3_title, R.string.onboarding_slide3_desc),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = OnboardingPagerAdapter(slides)
        binding.viewPager.adapter = adapter
        setupDots(slides.size)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                val isLast = position == slides.size - 1
                binding.btnNext.text = getString(
                    if (isLast) R.string.onboarding_btn_start else R.string.onboarding_btn_next
                )
                binding.btnSkip.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
            }
        })

        binding.btnNext.addScaleFeedback()
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < slides.size - 1) {
                binding.viewPager.currentItem = current + 1
            } else {
                finish()
            }
        }

        binding.btnSkip.setOnClickListener { finish() }
    }

    private fun setupDots(count: Int) {
        binding.indicatorDots.removeAllViews()
        repeat(count) { i ->
            val dot = ImageView(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(dpToPx(8), dpToPx(8))
                    .also { it.setMargins(dpToPx(4), 0, dpToPx(4), 0) }
                setImageDrawable(GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (i == 0) R.color.primary else R.color.primary_light
                        )
                    )
                })
            }
            binding.indicatorDots.addView(dot)
        }
    }

    private fun updateDots(active: Int) {
        for (i in 0 until binding.indicatorDots.childCount) {
            val dot = binding.indicatorDots.getChildAt(i) as? ImageView ?: continue
            val isActive = i == active
            dot.animate()
                .scaleX(if (isActive) 1.3f else 1f)
                .scaleY(if (isActive) 1.3f else 1f)
                .setDuration(200).start()
            (dot.drawable as? GradientDrawable)?.setColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isActive) R.color.primary else R.color.primary_light
                )
            )
        }
    }

    private fun finish() {
        requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_done", true).apply()
        findNavController().navigate(R.id.action_onboarding_to_scan)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
