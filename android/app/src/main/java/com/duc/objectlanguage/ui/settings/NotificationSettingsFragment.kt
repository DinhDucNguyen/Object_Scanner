package com.duc.objectlanguage.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.NotificationPreferences
import com.duc.objectlanguage.data.local.NotificationSettings
import com.duc.objectlanguage.databinding.FragmentNotificationSettingsBinding
import com.duc.objectlanguage.utils.AppNotificationHelper
import com.duc.objectlanguage.workers.DailyReminderWorker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch

class NotificationSettingsFragment : Fragment() {

    private var _binding: FragmentNotificationSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferences: NotificationPreferences
    private var latestSettings = NotificationSettings()
    private var rendering = false
    private var enableDailyAfterPermission = false
    private var sendTestAfterPermission = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && enableDailyAfterPermission) {
            enableDailyReminder()
        } else if (granted && sendTestAfterPermission) {
            sendTestNotification()
        } else if (granted) {
            updateStatus(getString(R.string.notification_permission_granted))
        } else if (!granted) {
            setDailySwitchChecked(false)
            updateStatus(getString(R.string.notification_permission_denied))
        }
        enableDailyAfterPermission = false
        sendTestAfterPermission = false
        updatePermissionState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = NotificationPreferences(requireContext().applicationContext)

        setupListeners()
        observeSettings()
        updatePermissionState()
    }

    private fun setupListeners() {
        binding.dailyReminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (rendering) return@setOnCheckedChangeListener
            if (isChecked) {
                requestPermissionThenEnable()
            } else {
                disableDailyReminder()
            }
        }

        binding.selectTimeButton.setOnClickListener {
            showTimePickerDialog()
        }

        binding.notificationPermissionButton.setOnClickListener {
            requestNotificationPermission(enableAfterGrant = false)
        }

        binding.sendTestButton.setOnClickListener {
            requestPermissionThenSendTest()
        }

        binding.streakAlertSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (rendering) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setStreakAlertEnabled(isChecked)
                updateStatus(
                    if (isChecked) getString(R.string.notification_streak_alert_enabled)
                    else getString(R.string.notification_streak_alert_disabled)
                )
            }
        }

        binding.milestoneCelebrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (rendering) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setMilestoneCelebrationEnabled(isChecked)
                updateStatus(
                    if (isChecked) getString(R.string.notification_milestone_enabled)
                    else getString(R.string.notification_milestone_disabled)
                )
            }
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.settings.collect { settings ->
                    latestSettings = settings
                    render(settings)
                }
            }
        }
    }

    private fun render(settings: NotificationSettings) {
        rendering = true
        binding.dailyReminderSwitch.isChecked = settings.dailyReminderEnabled
        binding.streakAlertSwitch.isChecked = settings.streakAlertEnabled
        binding.milestoneCelebrationSwitch.isChecked = settings.milestoneCelebrationEnabled
        rendering = false

        binding.selectedTimeText.text = formatTime(settings.reminderHour, settings.reminderMinute)
        binding.notificationSummaryText.text = if (settings.dailyReminderEnabled) {
            getString(R.string.notification_summary_enabled, formatTime(settings.reminderHour, settings.reminderMinute))
        } else {
            getString(R.string.notification_summary_disabled)
        }
    }

    private fun requestPermissionThenEnable() {
        if (hasNotificationPermission()) {
            enableDailyReminder()
        } else {
            requestNotificationPermission(enableAfterGrant = true)
        }
    }

    private fun enableDailyReminder() {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            preferences.setDailyReminderEnabled(true)
            DailyReminderWorker.scheduleDailyReminder(
                appContext,
                latestSettings.reminderHour,
                latestSettings.reminderMinute,
            )
            updateStatus(getString(R.string.notification_status_enabled, formatTime(latestSettings.reminderHour, latestSettings.reminderMinute)))
        }
    }

    private fun disableDailyReminder() {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            preferences.setDailyReminderEnabled(false)
            DailyReminderWorker.cancelDailyReminder(appContext)
            updateStatus(getString(R.string.notification_status_disabled))
        }
    }

    private fun showTimePickerDialog() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(latestSettings.reminderHour)
            .setMinute(latestSettings.reminderMinute)
            .setTitleText(getString(R.string.notification_time_picker_title))
            .build()

        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute
            val appContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setReminderTime(hour, minute)
                if (latestSettings.dailyReminderEnabled) {
                    DailyReminderWorker.scheduleDailyReminder(appContext, hour, minute)
                    updateStatus(getString(R.string.notification_status_enabled, formatTime(hour, minute)))
                } else {
                    updateStatus(getString(R.string.notification_time_saved, formatTime(hour, minute)))
                }
            }
        }

        picker.show(parentFragmentManager, "notification_time_picker")
    }

    private fun requestPermissionThenSendTest() {
        if (!hasNotificationPermission()) {
            sendTestAfterPermission = true
            requestNotificationPermission(enableAfterGrant = false)
            return
        }
        sendTestNotification()
    }

    private fun sendTestNotification() {
        AppNotificationHelper.showDailyReminder(
            requireContext().applicationContext,
            getString(R.string.notification_test_title),
            getString(R.string.notification_test_message),
        )
        updateStatus(getString(R.string.notification_test_sent))
    }

    private fun requestNotificationPermission(enableAfterGrant: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        enableDailyAfterPermission = enableAfterGrant
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun updatePermissionState() {
        val granted = hasNotificationPermission()
        binding.notificationPermissionText.text = if (granted) {
            getString(R.string.notification_permission_granted)
        } else {
            getString(R.string.notification_permission_required)
        }
        binding.notificationPermissionButton.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun setDailySwitchChecked(checked: Boolean) {
        rendering = true
        binding.dailyReminderSwitch.isChecked = checked
        rendering = false
    }

    private fun updateStatus(message: String) {
        binding.statusText.text = message
    }

    private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
