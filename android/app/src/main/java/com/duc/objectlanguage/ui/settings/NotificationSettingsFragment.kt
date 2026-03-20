package com.duc.objectlanguage.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.duc.objectlanguage.databinding.FragmentNotificationSettingsBinding
import com.duc.objectlanguage.workers.DailyReminderWorker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

/**
 * Fragment for managing notification preferences
 */
class NotificationSettingsFragment : Fragment() {
    
    private var _binding: FragmentNotificationSettingsBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwitches()
        setupTimeSelector()
    }
    
    private fun setupSwitches() {
        // Daily reminder switch
        binding.dailyReminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showTimePickerDialog()
            } else {
                DailyReminderWorker.cancelDailyReminder(requireContext())
                updateStatus("Daily reminders disabled")
            }
        }
        
        // Streak alert switch
        binding.streakAlertSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                updateStatus("Streak alerts enabled - You'll be notified if your streak is at risk")
            } else {
                updateStatus("Streak alerts disabled")
            }
        }
        
        // Milestone celebration switch
        binding.milestoneCelebrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                updateStatus("🎉 Milestone celebrations enabled!")
            } else {
                updateStatus("Milestone celebrations disabled")
            }
        }
    }
    
    private fun setupTimeSelector() {
        binding.selectTimeButton.setOnClickListener {
            showTimePickerDialog()
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
            
            val timeText = String.format("%02d:%02d", hour, minute)
            binding.selectedTimeText.text = timeText
            binding.dailyReminderSwitch.isChecked = true
            updateStatus("✅ Daily reminder set for $timeText")
        }
        
        picker.show(parentFragmentManager, "time_picker")
    }
    
    private fun updateStatus(message: String) {
        binding.statusText.text = message
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
