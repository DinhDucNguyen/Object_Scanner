package com.duc.objectlanguage

import android.app.Application
import android.content.Context
import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.repository.AppRepository
import com.duc.objectlanguage.utils.LocaleHelper
import com.duc.objectlanguage.workers.DailyReminderWorker
import com.duc.objectlanguage.workers.StreakResetWorker

class ObjectLanguageApp : Application() {

    lateinit var tokenManager: TokenManager
    lateinit var repository: AppRepository

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)

        // Initialize Retrofit client with TokenManager
        RetrofitClient.init(tokenManager)

        repository = AppRepository(tokenManager)

        // Schedule background workers for streaks and notifications
        StreakResetWorker.scheduleStreakCheck(this)
        DailyReminderWorker.scheduleDailyReminder(this, hourOfDay = 19, minute = 0)
    }
}
