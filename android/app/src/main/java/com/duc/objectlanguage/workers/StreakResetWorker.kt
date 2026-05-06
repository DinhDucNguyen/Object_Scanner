package com.duc.objectlanguage.workers

import android.content.Context
import androidx.work.*
import com.duc.objectlanguage.data.local.StreakDataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Worker to check and reset streaks if more than 1 day passed
 */
class StreakResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val WORK_NAME = "streak_reset_check"
        
        /**
         * Schedule periodic streak validation
         */
        fun scheduleStreakCheck(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<StreakResetWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
    
    override suspend fun doWork(): Result {
        val streakDataStore = StreakDataStore(applicationContext)
        
        // Read current streak value once (triggers validation)
        streakDataStore.currentStreak.first()
        
        return Result.success()
    }
}

