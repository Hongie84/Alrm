package com.alrm

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alrm.scheduler.CalendarAlarmWorker
import java.util.concurrent.TimeUnit

class AlrmApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Schedule a daily worker that re-evaluates calendar-based alarms
        val dailyCheck = PeriodicWorkRequestBuilder<CalendarAlarmWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "calendar_alarm_check",
            ExistingPeriodicWorkPolicy.KEEP,
            dailyCheck
        )
    }
}
