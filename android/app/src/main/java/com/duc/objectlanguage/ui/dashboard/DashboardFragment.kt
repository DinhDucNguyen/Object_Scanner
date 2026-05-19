package com.duc.objectlanguage.ui.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentDashboardBinding
import com.google.android.material.card.MaterialCardView

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private var missionOpensReview = false
    private val suggestionSlots get() = listOf(
        SuggestionSlot(
            binding.cardSuggestionOne.root,
            binding.cardSuggestionOne.tvSuggestionTitle,
            binding.cardSuggestionOne.ivSuggestionIcon,
        ),
        SuggestionSlot(
            binding.cardSuggestionTwo.root,
            binding.cardSuggestionTwo.tvSuggestionTitle,
            binding.cardSuggestionTwo.ivSuggestionIcon,
        ),
        SuggestionSlot(
            binding.cardSuggestionThree.root,
            binding.cardSuggestionThree.tvSuggestionTitle,
            binding.cardSuggestionThree.ivSuggestionIcon,
        ),
        SuggestionSlot(
            binding.cardSuggestionFour.root,
            binding.cardSuggestionFour.tvSuggestionTitle,
            binding.cardSuggestionFour.ivSuggestionIcon,
        ),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnScanNow.setOnClickListener {
            if (missionOpensReview) {
                openReview()
            } else {
                openScan()
            }
        }

        binding.cardDashboardSearch.setOnClickListener {
            openDictionary()
        }

        suggestionSlots.forEach { slot ->
            slot.card.setOnClickListener { openScan() }
        }

        binding.cardDashboardStreak.setOnClickListener {
            findNavController().navigate(com.duc.objectlanguage.R.id.streakFragment)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                bindMission(stats.dueToday, stats.totalScans)

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

        viewModel.dailySuggestions.observe(viewLifecycleOwner) { suggestions ->
            bindDailySuggestions(suggestions)
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loadStats()
        viewModel.loadStreak()
        viewModel.loadDailySuggestions()
    }

    private fun openScan() {
        selectBottomTab(R.id.scanFragment)
    }

    private fun openReview() {
        selectBottomTab(R.id.reviewFragment)
    }

    private fun openDictionary() {
        selectBottomTab(R.id.dictionaryFragment)
    }

    private fun selectBottomTab(itemId: Int) {
        val bottomNav = requireActivity()
            .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
        if (bottomNav != null) {
            bottomNav.selectedItemId = itemId
        } else {
            findNavController().navigate(itemId)
        }
    }

    private fun bindMission(dueToday: Int, totalScans: Int) {
        missionOpensReview = dueToday > 0
        if (dueToday > 0) {
            binding.tvObjectOfDayContainer.visibility = View.GONE
            binding.tvMissionTitle.text = getString(R.string.dashboard_mission_review_title, dueToday)
            binding.tvMissionSubtitle.text = getString(R.string.dashboard_mission_review_subtitle)
            binding.btnScanNow.text = getString(R.string.dashboard_btn_review_short)
            binding.btnScanNow.setIconResource(R.drawable.ic_book)
            binding.btnScanNow.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.secondary)
            )
            binding.btnScanNow.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_primary))
            binding.btnScanNow.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.text_on_primary)
            )
        } else {
            binding.tvObjectOfDayContainer.visibility = View.VISIBLE
            binding.tvMissionTitle.text = getString(
                if (totalScans == 0) R.string.dashboard_mission_first_scan_title
                else R.string.dashboard_mission_scan_title
            )
            binding.tvMissionSubtitle.text = getString(
                if (totalScans == 0) R.string.dashboard_mission_first_scan_subtitle
                else R.string.dashboard_mission_scan_subtitle
            )
            binding.btnScanNow.text = getString(R.string.dashboard_btn_scan_now)
            binding.btnScanNow.setIconResource(R.drawable.ic_camera)
            binding.btnScanNow.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.primary)
            )
            binding.btnScanNow.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_primary))
            binding.btnScanNow.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.text_on_primary)
            )
        }
    }

    private fun bindDailySuggestions(suggestions: List<DashboardSuggestion>) {
        suggestions.firstOrNull()?.let { first ->
            binding.tvObjectOfDay.text = first.displayName
        }
        suggestionSlots.forEachIndexed { index, slot ->
            val suggestion = suggestions.getOrNull(index)
            if (suggestion == null) {
                slot.card.visibility = View.GONE
            } else {
                val accent = getSuggestionAccent(index)
                slot.card.visibility = View.VISIBLE
                slot.title.text = suggestion.displayName
                slot.card.contentDescription = suggestion.objectCode
                slot.card.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), accent.backgroundColorRes)
                )
                slot.card.setStrokeColor(ContextCompat.getColor(requireContext(), accent.backgroundColorRes))
                slot.icon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), accent.iconColorRes)
                )
            }
        }
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

        binding.cardDashboardStreak.setStrokeColor(trackColor)
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
        else -> StreakAccent(R.color.warning, R.color.warning_light)
    }

    private fun getSuggestionAccent(index: Int): SuggestionAccent = when (index % 4) {
        0 -> SuggestionAccent(R.color.primary_light, R.color.primary)
        1 -> SuggestionAccent(R.color.success_light, R.color.success)
        2 -> SuggestionAccent(R.color.cyan_light, R.color.cyan)
        else -> SuggestionAccent(R.color.violet_light, R.color.violet)
    }

    private data class StreakAccent(
        @ColorRes val colorRes: Int,
        @ColorRes val trackColorRes: Int,
    )

    private data class SuggestionAccent(
        @ColorRes val backgroundColorRes: Int,
        @ColorRes val iconColorRes: Int,
    )

    private data class SuggestionSlot(
        val card: MaterialCardView,
        val title: TextView,
        val icon: ImageView,
    )
}
