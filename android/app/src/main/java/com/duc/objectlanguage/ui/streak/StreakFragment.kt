package com.duc.objectlanguage.ui.streak

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.StreakCalendarDay
import com.duc.objectlanguage.databinding.FragmentStreakBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import androidx.navigation.fragment.findNavController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class StreakFragment : Fragment() {

    private var _binding: FragmentStreakBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StreakViewModel by viewModels()
    private var hasAnimatedProgress = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStreakBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        setupButtons()
        animateEntrance()
        viewModel.loadFromServer()
    }

    private fun observeViewModel() {
        var previousStreak = -1
        viewModel.streakData.observe(viewLifecycleOwner) { data ->
            updateUI(data)
            if (previousStreak >= 0 && data.currentStreak > previousStreak) {
                celebrateStreakIncrease()
            }
            previousStreak = data.currentStreak
        }
        viewModel.celebrateMilestone.observe(viewLifecycleOwner) { milestone ->
            if (milestone != null) {
                celebrateMilestone(milestone)
                viewModel.clearCelebration()
            }
        }
        viewModel.calendarDays.observe(viewLifecycleOwner) { days ->
            bindCalendar(days)
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun updateUI(data: StreakData) {
        binding.apply {
            val accent = getStreakAccent(data.currentStreak)
            val accentColor = ContextCompat.getColor(requireContext(), accent.colorRes)

            // Số ngày chuỗi
            currentStreakText.text = data.currentStreak.toString()
            currentStreakLabel.text = getString(
                if (data.currentStreak == 1) R.string.streak_days_singular
                else R.string.streak_days_plural
            ).uppercase()

            // Thống kê
            longestStreakText.text = getString(R.string.streak_days_count, data.longestStreak)
            totalReviewsText.text  = data.totalReviews.toString()
            reviewsTodayText.text  = data.reviewsToday.toString()

            pbLongestStreak.max = maxOf(data.longestStreak, 30)
            pbLongestStreak.progress = data.longestStreak
            pbTotalReviews.max = maxOf(data.totalReviews, 200)
            pbTotalReviews.progress = data.totalReviews
            pbReviewsToday.max = 10
            pbReviewsToday.progress = minOf(data.reviewsToday, 10)

            // Tiến trình hình tròn
            streakProgressCircle.max = data.nextMilestone
            if (!hasAnimatedProgress) {
                hasAnimatedProgress = true
                animateStreakCircle(data.currentStreak, data.nextMilestone)
            } else {
                streakProgressCircle.progress = data.currentStreak
            }
            streakProgressCircle.setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.warning))

            motivationText.text = buildMotivationMessage(data.currentStreak, data.reviewsToday)

            // Mốc tiếp theo
            nextMilestoneText.text = getString(R.string.streak_next_milestone_title, data.nextMilestone)
            milestoneProgressBar.max      = data.nextMilestone
            milestoneProgressBar.progress = data.currentStreak
            daysToMilestoneText.text = getString(R.string.streak_days_to_go, data.daysToMilestone)

            // Flame icon theo cường độ chuỗi
            flameIcon.imageTintList = if (data.currentStreak > 0) {
                null
            } else {
                ColorStateList.valueOf(accentColor)
            }

            // Trạng thái hôm nay
            if (data.reviewsToday > 0) {
                todayStatusIcon.text = getString(R.string.symbol_check)
                todayStatusText.text = getString(R.string.streak_today_done).uppercase()
                todayStatusContainer.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.success_light))
                todayStatusIcon.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                todayStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
            } else {
                todayStatusIcon.text = getString(R.string.symbol_close)
                todayStatusText.text = getString(R.string.streak_today_pending).uppercase()
                todayStatusContainer.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.warning_light))
                todayStatusIcon.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
                todayStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
            }

            // Thành tựu
            val streak = data.currentStreak
            
            // Achievement 1: Mới bắt đầu (3 ngày)
            if (streak >= 3) {
                achievement1Bg.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.warning_light))
                achievement1Icon.setImageResource(R.drawable.ic_award)
                achievement1Icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.warning))
                achievement1Label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            } else {
                achievement1Bg.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_soft))
                achievement1Icon.setImageResource(R.drawable.ic_lock)
                achievement1Icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_muted))
                achievement1Label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
            }

            // Achievement 2: 7 Ngày
            if (streak >= 7) {
                achievement2Bg.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_light))
                achievement2Icon.setImageResource(R.drawable.ic_verified)
                achievement2Icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary))
                achievement2Label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            } else {
                achievement2Bg.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_soft))
                achievement2Icon.setImageResource(R.drawable.ic_lock)
                achievement2Icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_muted))
                achievement2Label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
            }

            // Achievement 3: 30 Ngày
            if (streak >= 30) {
                achievement3Bg.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.violet_light))
                achievement3Icon.setImageResource(R.drawable.ic_award)
                achievement3Icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.violet))
                achievement3Label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            } else {
                achievement3Bg.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_soft))
                achievement3Icon.setImageResource(R.drawable.ic_lock)
                achievement3Icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_muted))
                achievement3Label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
            }

            // Achievement 4: Chuyên cần (Mốc 50 ngày)
            if (streak >= 50) {
                achievement4Bg.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.cyan_light))
                achievement4Icon.setImageResource(R.drawable.ic_star)
                achievement4Icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.cyan))
                achievement4Label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            } else {
                achievement4Bg.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_soft))
                achievement4Icon.setImageResource(R.drawable.ic_lock)
                achievement4Icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_muted))
                achievement4Label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
            }
        }
    }

    private fun buildMotivationMessage(streak: Int, reviewsToday: Int): String = when {
        reviewsToday == 0 && streak == 0 -> getString(R.string.streak_msg_start)
        reviewsToday == 0 && streak > 0  -> getString(R.string.streak_msg_dont_break, streak)
        reviewsToday > 0  && streak == 0 -> getString(R.string.streak_msg_great_start)
        streak == 1                       -> getString(R.string.streak_msg_keep_going)
        streak in 2..6                    -> getString(R.string.streak_msg_momentum, streak)
        streak in 7..13                   -> getString(R.string.streak_msg_one_week)
        streak in 14..29                  -> getString(R.string.streak_msg_two_weeks)
        streak in 30..99                  -> getString(R.string.streak_msg_champion, streak)
        streak >= 100                     -> getString(R.string.streak_msg_legend, streak)
        else                              -> getString(R.string.streak_msg_default)
    }

    private fun getStreakAccent(currentStreak: Int): StreakAccent = when {
        currentStreak == 0 -> StreakAccent(R.color.streak_idle)
        currentStreak in 1..2 -> StreakAccent(R.color.streak_heat_1)
        currentStreak in 3..6 -> StreakAccent(R.color.streak_heat_2)
        currentStreak in 7..13 -> StreakAccent(R.color.streak_heat_3)
        else -> StreakAccent(R.color.streak_heat_4)
    }

    private data class StreakAccent(
        @ColorRes val colorRes: Int,
    )

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.milestoneInfoButton.setOnClickListener { showMilestoneInfo() }
        binding.btnLearnNow.setOnClickListener {
            selectBottomTab(R.id.reviewFragment)
        }
        binding.streakCircleContainer.setOnClickListener {
            viewModel.streakData.value?.let { data ->
                animateStreakCircle(data.currentStreak, data.nextMilestone)
            }
        }
    }

    private fun animateEntrance() {
        // Hero circle: scale + fade bounce
        binding.streakCircleContainer.apply {
            scaleX = 0.7f; scaleY = 0.7f; alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(500).setInterpolator(OvershootInterpolator(1.3f))
                .setStartDelay(80).start()
        }
        // Motivation text: fade in
        binding.motivationText.apply {
            alpha = 0f; translationY = 16f
            animate().alpha(1f).translationY(0f)
                .setDuration(300).setInterpolator(DecelerateInterpolator())
                .setStartDelay(300).start()
        }
        // Achievement cards: stagger vào từ dưới
        listOf(
            binding.achievement1Bg,
            binding.achievement2Bg,
            binding.achievement3Bg,
            binding.achievement4Bg,
        ).forEachIndexed { i, card ->
            card.alpha = 0f; card.translationY = 24f
            card.animate().alpha(1f).translationY(0f)
                .setDuration(260).setStartDelay(420L + i * 70L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }
    }

    private fun animateStreakCircle(currentStreak: Int, nextMilestone: Int) {
        binding.streakProgressCircle.max = nextMilestone
        
        val animator = android.animation.ValueAnimator.ofInt(0, currentStreak)
        animator.duration = 1200 // 1.2s
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            if (_binding != null) {
                binding.streakProgressCircle.setProgressCompat(animation.animatedValue as Int, false)
            }
        }
        animator.start()
    }

    private fun selectBottomTab(itemId: Int) {
        findNavController().navigate(itemId)
    }

    private fun showMilestoneInfo() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_streak_milestone, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnMilestoneOk)
            .setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun celebrateStreakIncrease() {
        val party = Party(
            speed = 5f,
            maxSpeed = 20f,
            damping = 0.9f,
            spread = 60,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d),
            emitter = Emitter(duration = 1, TimeUnit.SECONDS).max(40),
            position = Position.Relative(0.5, 0.0)
        )
        binding.konfettiView.start(party)
    }

    fun celebrateMilestone(milestone: Int) {
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
            emitter = Emitter(duration = 3, TimeUnit.SECONDS).max(100),
            position = Position.Relative(0.5, 0.3)
        )
        binding.konfettiView.start(party)

        val dialogView = layoutInflater.inflate(R.layout.dialog_streak_celebrate, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        dialogView.findViewById<android.widget.TextView>(R.id.tvCelebrateMsg)
            .text = getString(R.string.streak_celebrate_msg, milestone)
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCelebrateOk)
            .setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun bindCalendar(days: List<StreakCalendarDay>) {
        val container = binding.gridCalendar
        container.removeAllViews()
        if (days.isEmpty()) return

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val firstCal = Calendar.getInstance().apply { time = fmt.parse(days.first().date)!! }
        // DAY_OF_WEEK: 1=CN, 2=T2 ... 7=T7 → offset 0-based
        val firstDow = firstCal.get(Calendar.DAY_OF_WEEK) - 1
        val todayStr = fmt.format(Calendar.getInstance().time)

        // Tạo mảng slot: firstDow ô null + các ngày thực
        val slots: MutableList<StreakCalendarDay?> = MutableList(firstDow) { null }
        slots.addAll(days)
        // Pad cuối để đủ hàng
        while (slots.size % 7 != 0) slots.add(null)

        val numRows = slots.size / 7
        val ctx = requireContext()

        for (row in 0 until numRows) {
            val rowLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            for (col in 0 until 7) {
                val slot = slots[row * 7 + col]
                val isToday = slot?.date == todayStr

                val cellFrame = android.widget.FrameLayout(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                if (slot != null) {
                    val dp28 = (28 * resources.displayMetrics.density).toInt()
                    val dp36 = (36 * resources.displayMetrics.density).toInt()
                    val iconSize = if (isToday) dp36 else dp28

                    // Background tròn highlight cho hôm nay
                    if (isToday) {
                        val bg = View(ctx).apply {
                            val lp = android.widget.FrameLayout.LayoutParams(dp36, dp36)
                            lp.gravity = android.view.Gravity.CENTER
                            layoutParams = lp
                            background = ContextCompat.getDrawable(ctx, R.drawable.bg_calendar_today)
                        }
                        cellFrame.addView(bg)
                    }

                    val icon = ImageView(ctx).apply {
                        val lp = android.widget.FrameLayout.LayoutParams(iconSize, iconSize)
                        lp.gravity = android.view.Gravity.CENTER
                        layoutParams = lp
                        setImageResource(R.drawable.ic_fire)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                        if (slot.count == 0) {
                            // Lửa xám chuẩn: desaturate hoàn toàn
                            val cm = ColorMatrix().apply { setSaturation(0f) }
                            colorFilter = ColorMatrixColorFilter(cm)
                        }
                    }

                    if (isToday) {
                        icon.scaleX = 0.4f; icon.scaleY = 0.4f
                        icon.animate().scaleX(1f).scaleY(1f)
                            .setDuration(450)
                            .setInterpolator(OvershootInterpolator(2.2f))
                            .setStartDelay(row * 50L + 100)
                            .start()
                    }

                    cellFrame.addView(icon)
                }

                rowLayout.addView(cellFrame)
            }

            // Fade-in từng hàng với stagger
            rowLayout.alpha = 0f
            rowLayout.animate()
                .alpha(1f)
                .setDuration(200)
                .setStartDelay(row * 50L)
                .start()

            container.addView(rowLayout)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
