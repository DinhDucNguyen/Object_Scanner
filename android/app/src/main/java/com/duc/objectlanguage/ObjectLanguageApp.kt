package com.duc.objectlanguage

import android.app.Application
import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.api.RetrofitInstance
import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.repository.AppRepository
import com.duc.objectlanguage.workers.DailyReminderWorker
import com.duc.objectlanguage.workers.StreakResetWorker

class ObjectLanguageApp : Application() {

    lateinit var tokenManager: TokenManager
    lateinit var repository: AppRepository

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)

        // Initialize both Retrofit clients with TokenManager
        RetrofitClient.init(tokenManager)
        RetrofitInstance.init(tokenManager)

        repository = AppRepository(tokenManager)

        // Schedule background workers for streaks and notifications
        StreakResetWorker.scheduleStreakCheck(this)
        DailyReminderWorker.scheduleDailyReminder(this, hourOfDay = 19, minute = 0)
    }
}
