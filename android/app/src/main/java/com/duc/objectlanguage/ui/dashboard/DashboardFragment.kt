package com.duc.objectlanguage.ui.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStartReview.setOnClickListener {
            val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.duc.objectlanguage.R.id.bottomNavigation)
            if (bottomNav != null) {
                bottomNav.selectedItemId = com.duc.objectlanguage.R.id.reviewFragment
            } else {
                findNavController().navigate(com.duc.objectlanguage.R.id.reviewFragment)
            }
        }

        binding.btnExploreTopics.setOnClickListener {
            findNavController().navigate(com.duc.objectlanguage.R.id.exploreFragment)
        }

        binding.cardDashboardStreak.setOnClickListener {
            findNavController().navigate(com.duc.objectlanguage.R.id.streakFragment)
        }

        val app = requireActivity().application as ObjectLanguageApp
        binding.tvWelcome.text = app.tokenManager.username ?: getString(R.string.dashboard_default_user)

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                binding.tvTotalScans.text = "${stats.totalScans}"
                binding.tvTotalLearned.text = "${stats.totalLearned}"
                binding.tvDueToday.text = "${stats.dueToday}"
                binding.tvMastered.text = "${stats.mastered}"

                val total = stats.totalLearned
                val mastered = stats.mastered
                val pct = if (total > 0) (mastered * 100 / total) else 0
                binding.progressMastery.progress = pct
                binding.tvProgressPct.text = getString(R.string.dashboard_progress_short, pct)
            }
        }

        viewModel.streakSummary.observe(viewLifecycleOwner) { streak ->
            bindStreakSummary(streak)
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loadStats()
        viewModel.loadStreak()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStats()
        viewModel.loadStreak()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindStreakSummary(streak: DashboardStreakSummary) {
        val accent = getStreakAccent(streak.currentStreak)
        val accentColor = ContextCompat.getColor(requireContext(), accent.colorRes)
        val trackColor = ContextCompat.getColor(requireContext(), accent.trackColorRes)

        binding.cardDashboardStreak.setStrokeColor(accentColor)
        binding.ivDashboardStreakIcon.imageTintList = if (streak.reviewsToday > 0) {
            null
        } else {
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_muted))
        }
        binding.tvDashboardStreakValue.setTextColor(accentColor)
        binding.progressDashboardStreak.progressTintList = ColorStateList.valueOf(accentColor)
        binding.progressDashboardStreak.progressBackgroundTintList = ColorStateList.valueOf(trackColor)

        binding.tvDashboardStreakValue.text = streak.currentStreak.toString()
        binding.tvDashboardStreakDays.text = getString(
            if (streak.currentStreak == 1) R.string.streak_days_singular else R.string.streak_days_plural
        )
        binding.tvDashboardStreakStatus.text = getString(
            if (streak.reviewsToday > 0) R.string.dashboard_streak_today_done
            else R.string.dashboard_streak_today_pending
        )
        binding.tvDashboardStreakStatus.setTextColor(
            if (streak.reviewsToday > 0) accentColor
            else ContextCompat.getColor(requireContext(), R.color.text_secondary)
        )
        binding.tvDashboardStreakReviewsToday.text =
            getString(R.string.dashboard_streak_today_count, streak.reviewsToday)

        binding.tvDashboardStreakMilestone.text = if (streak.hasReachedTopMilestone) {
            getString(R.string.dashboard_streak_top_milestone)
        } else {
            getString(
                R.string.dashboard_streak_next_milestone,
                streak.daysToMilestone,
                streak.nextMilestone
            )
        }

        binding.progressDashboardStreak.progress = if (streak.hasReachedTopMilestone) {
            100
        } else {
            (streak.currentStreak * 100 / streak.nextMilestone).coerceIn(0, 100)
        }
    }

    private fun getStreakAccent(currentStreak: Int): StreakAccent = when {
        currentStreak == 0 -> StreakAccent(R.color.streak_idle, R.color.streak_idle_track)
        currentStreak in 1..2 -> StreakAccent(R.color.streak_heat_1, R.color.streak_heat_1_track)
        currentStreak in 3..6 -> StreakAccent(R.color.streak_heat_2, R.color.streak_heat_2_track)
        currentStreak in 7..13 -> StreakAccent(R.color.streak_heat_3, R.color.streak_heat_3_track)
        else -> StreakAccent(R.color.streak_heat_4, R.color.streak_heat_4_track)
    }

    private data class StreakAccent(
        @ColorRes val colorRes: Int,
        @ColorRes val trackColorRes: Int,
    )
}
