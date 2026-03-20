package com.duc.objectlanguage.ui.streak

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.duc.objectlanguage.databinding.FragmentStreakBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.duc.objectlanguage.workers.DailyReminderWorker
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

/**
 * Fragment showing streak statistics and motivation
 */
class StreakFragment : Fragment() {
    
    private var _binding: FragmentStreakBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: StreakViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStreakBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        setupButtons()
    }
    
    private fun observeViewModel() {
        // Streak data with motivation
        viewModel.streakData.observe(viewLifecycleOwner) { data ->
            updateUI(data)
        }
    }
    
    private fun updateUI(data: StreakData) {
        binding.apply {
            // Main streak display
            currentStreakText.text = data.currentStreak.toString()
            currentStreakLabel.text = if (data.currentStreak == 1) "day" else "days"
            
            // Statistics
            longestStreakText.text = data.longestStreak.toString()
            totalReviewsText.text = data.totalReviews.toString()
            reviewsTodayText.text = data.reviewsToday.toString()
            
            // Motivation message
            motivationText.text = data.motivationMessage
            
            // Progress to next milestone
            nextMilestoneText.text = "Next milestone: ${data.nextMilestone} days"
            milestoneProgressBar.max = data.nextMilestone
            milestoneProgressBar.progress = data.currentStreak
            daysToMilestoneText.text = "${data.daysToMilestone} days to go"
            
            // Flame icon intensity based on streak
            updateFlameIntensity(data.currentStreak)
            
            // Check if completed today
            if (data.reviewsToday > 0) {
                todayStatusIcon.text = "✅"
                todayStatusText.text = "Completed today!"
            } else {
                todayStatusIcon.text = "⏰"
                todayStatusText.text = "Review pending"
            }
        }
    }
    
    private fun updateFlameIntensity(streak: Int) {
        binding.flameIcon.text = when {
            streak == 0 -> "💤"
            streak in 1..2 -> "🔥"
            streak in 3..6 -> "🔥🔥"
            streak in 7..13 -> "🔥🔥🔥"
            streak >= 14 -> "🔥🔥🔥🔥"
            else -> "🔥"
        }
    }
    
    private fun setupButtons() {
        // Setup notification button
        binding.setupNotificationButton.setOnClickListener {
            showTimePickerDialog()
        }
        
        // Reset streak button (for testing/debugging)
        binding.resetStreakButton.setOnClickListener {
            showResetConfirmation()
        }
        
        // Milestone info button
        binding.milestoneInfoButton.setOnClickListener {
            showMilestoneInfo()
        }
    }
    
    private fun showTimePickerDialog() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(19)
            .setMinute(0)
            .setTitleText("Select reminder time")
            .build()
        
        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute
            
            // Schedule daily reminder
            DailyReminderWorker.scheduleDailyReminder(requireContext(), hour, minute)
            
            binding.notificationStatusText.text = "✅ Daily reminder set for ${String.format("%02d:%02d", hour, minute)}"
        }
        
        picker.show(parentFragmentManager, "time_picker")
    }
    
    private fun showResetConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset Streak?")
            .setMessage("This will reset all your streak data. This action cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                viewModel.resetStreak()
                binding.motivationText.text = "Streak reset. Start fresh!"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showMilestoneInfo() {
        val milestones = """
            🎯 Milestone Rewards:
            
            3 days   - Beginner Badge
            7 days   - Week Warrior
            14 days  - Two Week Champion
            30 days  - Monthly Master
            50 days  - Consistency King
            100 days - Century Club
            365 days - Year Legend
            
            Keep your daily streak to unlock achievements!
        """.trimIndent()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Streak Milestones")
            .setMessage(milestones)
            .setPositiveButton("Got it!", null)
            .show()
    }
    
    /**
     * Show confetti animation for milestone
     */
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
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🎉 Milestone Achieved!")
            .setMessage("Congratulations! You've reached a ${milestone}-day streak!\n\nYour dedication is inspiring. Keep it up!")
            .setPositiveButton("Awesome!", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
