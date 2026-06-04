package com.duc.objectlanguage.ui.dashboard

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentDashboardBinding
import com.duc.objectlanguage.ui.common.addScaleFeedback
import com.duc.objectlanguage.utils.resolveMediaUrl
import com.google.android.material.snackbar.Snackbar

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private var missionOpensReview = false
    private var goalIconAnimator: ObjectAnimator? = null
    private var hasStartedLearning: Boolean? = null
    private var latestStreakSummary: DashboardStreakSummary? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardDashboardSearch.setOnClickListener {
            openDictionary()
        }
        binding.cardDashboardSearch.addScaleFeedback()
        binding.btnScanNow.addScaleFeedback()
        binding.ivDashboardAvatar.addScaleFeedback()
        binding.ivDashboardAvatar.setOnClickListener {
            selectBottomTab(R.id.profileFragment)
        }

        binding.btnScanNow.setOnClickListener {
            if (missionOpensReview) openReview() else openScan()
        }

        binding.cardDashboardStreak.setOnClickListener {
            findNavController().navigate(R.id.streakFragment)
        }
        binding.btnStreakBadge.setOnClickListener {
            findNavController().navigate(R.id.streakFragment)
        }
        binding.cardExploreBanner.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_explore)
        }
        binding.btnSeeAllCollections.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_collectionList)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                binding.shimmerStats.visibility = View.VISIBLE
                binding.shimmerStats.startShimmer()
                binding.statsRow.visibility = View.INVISIBLE
            } else {
                binding.shimmerStats.stopShimmer()
                binding.shimmerStats.visibility = View.GONE
                binding.statsRow.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = false
            }
        }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadStats()
            viewModel.loadStreak()
            viewModel.loadTopCollections()
            viewModel.loadProfileSummary()
            binding.swipeRefresh.post {
                Snackbar.make(binding.root, R.string.refresh_success, Snackbar.LENGTH_SHORT).show()
            }
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                val total = stats.totalLearned
                val mastered = stats.mastered
                val pct = if (total > 0) (mastered * 100 / total) else 0
                animateProgress(binding.progressMastery, pct)
                animateCounter(binding.tvProgressPct, 0, pct, suffix = "%")
                bindMission(stats.dueToday, stats.totalScans)
                hasStartedLearning = stats.totalScans > 0 ||
                    stats.totalLearned > 0 ||
                    stats.mastered > 0 ||
                    stats.dueToday > 0
                latestStreakSummary?.let { bindStreakSummary(it) }
            }
        }

        viewModel.streakSummary.observe(viewLifecycleOwner) { streak ->
            latestStreakSummary = streak
            bindStreakSummary(streak)
        }

        viewModel.topCollections.observe(viewLifecycleOwner) { collections ->
            bindTopCollections(collections)
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Snackbar.make(binding.root, err, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.btn_retry)) {
                        viewModel.loadStats()
                        viewModel.loadStreak()
                    }
                    .show()
            }
        }

        viewModel.avatarUrl.observe(viewLifecycleOwner) { url ->
            if (!url.isNullOrBlank()) {
                Glide.with(this)
                    .load(resolveMediaUrl(url))
                    .transform(CircleCrop())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(binding.ivDashboardAvatar)
                binding.ivDashboardAvatar.setPadding(0, 0, 0, 0)
                binding.ivDashboardAvatar.imageTintList = null
            }
        }
        viewModel.displayName.observe(viewLifecycleOwner) { name ->
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val greetingRes = when {
                hour in 6..11 -> R.string.dashboard_greeting_morning
                hour < 18 -> R.string.dashboard_greeting_afternoon
                else -> R.string.dashboard_greeting_evening
            }
            binding.tvDashboardGreeting.text = getString(greetingRes, name)
        }

        viewModel.loadStats()
        viewModel.loadStreak()
        viewModel.loadTopCollections()
        viewModel.loadProfileSummary()
        animateDashboardEntrance()
        startGoalIconFloatAnimation()
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
        findNavController().navigate(itemId)
    }

    private fun bindTopCollections(collections: List<CollectionHighlight>) {
        if (collections.isEmpty()) {
            binding.sectionTopCollections.visibility = View.GONE
            return
        }
        val wasGone = binding.sectionTopCollections.visibility != View.VISIBLE
        binding.sectionTopCollections.visibility = View.VISIBLE
        if (wasGone) {
            binding.sectionTopCollections.alpha = 0f
            binding.sectionTopCollections.translationY = 32f
            binding.sectionTopCollections.animate()
                .alpha(1f).translationY(0f)
                .setDuration(340)
                .setStartDelay(260)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        }
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
                if (wasGone) {
                    card.alpha = 0f
                    card.scaleX = 0.88f
                    card.scaleY = 0.88f
                    card.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(260)
                        .setStartDelay(320L + index * 70L)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.3f))
                        .start()
                }
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
        viewModel.loadProfileSummary()
    }

    private fun animateCounter(view: TextView, from: Int, to: Int, suffix: String = "") {
        ValueAnimator.ofInt(from, to).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                if (!isAdded) return@addUpdateListener
                view.text = getString(R.string.format_number_suffix, it.animatedValue as Int, suffix)
            }
            start()
        }
    }

    private fun animateProgress(bar: ProgressBar, to: Int) {
        ObjectAnimator.ofInt(bar, "progress", 0, to).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun animateDashboardEntrance() {
        val sections = listOf(
            binding.cardDashboardSearch,
            binding.statsRow,
            binding.cardExploreBanner,
            binding.cardDailyGoal,
        )
        sections.forEachIndexed { i, view ->
            view.alpha = 0f
            view.translationY = 48f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(380)
                .setStartDelay(80L + i * 90L)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()
        }
    }

    private fun startGoalIconFloatAnimation() {
        goalIconAnimator?.cancel()
        val density = resources.displayMetrics.density
        val floatDistance = -14f * density // 14dp float distance
        goalIconAnimator = ObjectAnimator.ofFloat(binding.ivGoalIcon, View.TRANSLATION_Y, 0f, floatDistance, 0f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onDestroyView() {
        goalIconAnimator?.cancel()
        goalIconAnimator = null
        super.onDestroyView()
        _binding = null
    }

    private fun bindStreakSummary(streak: DashboardStreakSummary) {
        val accent = getStreakAccent(streak.currentStreak)
        val accentColor = ContextCompat.getColor(requireContext(), accent.colorRes)
        val trackColor = ContextCompat.getColor(requireContext(), accent.trackColorRes)
        val isNewAccount = hasStartedLearning == false &&
            streak.currentStreak == 0 &&
            streak.reviewsToday == 0

        binding.cardDashboardStreak.setStrokeColor(trackColor)
        binding.ivDashboardStreakIcon.imageTintList = if (streak.reviewsToday > 0) {
            null
        } else {
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_muted))
        }
        binding.tvDashboardStreakValue.setTextColor(accentColor)
        binding.progressDashboardStreak.progressTintList = ColorStateList.valueOf(accentColor)
        binding.progressDashboardStreak.progressBackgroundTintList = ColorStateList.valueOf(trackColor)

        animateCounter(binding.tvDashboardStreakValue, 0, streak.currentStreak)
        animateCounter(binding.tvStreakBadgeValue, 0, streak.currentStreak)
        binding.tvDashboardStreakDays.text = getString(
            if (streak.currentStreak == 1) R.string.streak_days_singular else R.string.streak_days_plural
        )
        binding.tvDashboardStreakStatus.text = when {
            isNewAccount -> getString(R.string.dashboard_streak_welcome_title)
            streak.reviewsToday > 0 -> getString(R.string.dashboard_streak_today_done)
            else -> getString(R.string.dashboard_streak_today_pending)
        }
        binding.tvDashboardStreakStatus.setTextColor(
            if (streak.reviewsToday > 0) accentColor
            else ContextCompat.getColor(requireContext(), R.color.text_secondary)
        )
        binding.tvDashboardStreakReviewsToday.text = if (isNewAccount) {
            getString(R.string.dashboard_streak_welcome_hint)
        } else {
            getString(R.string.dashboard_streak_today_count, streak.reviewsToday)
        }

        binding.tvDashboardStreakMilestone.text = when {
            isNewAccount -> getString(R.string.dashboard_streak_welcome_milestone)
            streak.hasReachedTopMilestone -> getString(R.string.dashboard_streak_top_milestone)
            else -> getString(
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
