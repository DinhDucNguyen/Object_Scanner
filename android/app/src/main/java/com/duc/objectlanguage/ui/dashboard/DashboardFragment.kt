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
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private var missionOpensReview = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardDashboardSearch.setOnClickListener {
            openDictionary()
        }

        binding.btnScanNow.setOnClickListener {
            if (missionOpensReview) openReview() else openScan()
        }

        binding.cardDashboardStreak.setOnClickListener {
            findNavController().navigate(com.duc.objectlanguage.R.id.streakFragment)
        }
        binding.cardExploreBanner.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_explore)
        }
        binding.btnSeeAllCollections.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_collectionList)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                val total = stats.totalLearned
                val mastered = stats.mastered
                val pct = if (total > 0) (mastered * 100 / total) else 0
                binding.progressMastery.progress = pct
                binding.tvProgressPct.text = getString(R.string.dashboard_progress_short, pct)
                bindMission(stats.dueToday, stats.totalScans)
            }
        }

        viewModel.streakSummary.observe(viewLifecycleOwner) { streak ->
            bindStreakSummary(streak)
        }

        viewModel.topCollections.observe(viewLifecycleOwner) { collections ->
            bindTopCollections(collections)
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loadStats()
        viewModel.loadStreak()
        viewModel.loadTopCollections()
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

    private fun bindTopCollections(collections: List<CollectionHighlight>) {
        if (collections.isEmpty()) {
            binding.sectionTopCollections.visibility = View.GONE
            return
        }
        binding.sectionTopCollections.visibility = View.VISIBLE
        val cards = listOf(
            Triple(binding.cardHighlight1, binding.tvHighlightName1, binding.tvHighlightCount1),
            Triple(binding.cardHighlight2, binding.tvHighlightName2, binding.tvHighlightCount2),
            Triple(binding.cardHighlight3, binding.tvHighlightName3, binding.tvHighlightCount3),
        )
        cards.forEachIndexed { index, (card, name, count) ->
            val item = collections.getOrNull(index)
            if (item == null) {
                card.visibility = View.GONE
            } else {
                card.visibility = View.VISIBLE
                name.text = item.name
                count.text = getString(R.string.collection_item_count, item.itemCount)
                card.setOnClickListener {
                    val bundle = android.os.Bundle().apply { putInt("collectionId", item.id) }
                    findNavController().navigate(R.id.action_dashboard_to_collectionDetail, bundle)
                }
            }
        }
    }

    private fun bindMission(dueToday: Int, totalScans: Int) {
        when {
            totalScans == 0 -> {
                binding.tvObjectOfDayContainer.visibility = View.GONE
                binding.tvMissionTitle.text = getString(R.string.dashboard_mission_first_scan_title)
                binding.tvMissionSubtitle.text = getString(R.string.dashboard_mission_first_scan_subtitle)
                binding.btnScanNow.text = getString(R.string.dashboard_btn_scan_now)
                binding.btnScanNow.setIconResource(R.drawable.ic_camera)
                missionOpensReview = false
            }
            dueToday > 0 -> {
                binding.tvObjectOfDayContainer.visibility = View.GONE
                binding.tvMissionTitle.text = getString(R.string.dashboard_mission_review_title, dueToday)
                binding.tvMissionSubtitle.text = getString(R.string.dashboard_mission_review_subtitle)
                binding.btnScanNow.text = getString(R.string.dashboard_btn_review_short)
                binding.btnScanNow.setIconResource(R.drawable.ic_book)
                missionOpensReview = true
            }
            else -> {
                binding.tvObjectOfDayContainer.visibility = View.GONE
                binding.tvMissionTitle.text = getString(R.string.dashboard_mission_scan_title)
                binding.tvMissionSubtitle.text = getString(R.string.dashboard_mission_scan_subtitle)
                binding.btnScanNow.text = getString(R.string.dashboard_btn_scan_now)
                binding.btnScanNow.setIconResource(R.drawable.ic_camera)
                missionOpensReview = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStats()
        viewModel.loadStreak()
        viewModel.loadTopCollections()
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

    private data class StreakAccent(
        @ColorRes val colorRes: Int,
        @ColorRes val trackColorRes: Int,
    )
}
