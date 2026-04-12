package com.alrm.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alrm.alarm.AlarmDatabase
import com.alrm.alarm.ScheduleType
import com.alrm.receiver.CalendarHelper

/**
 * Runs daily to re-schedule calendar-based alarms for the coming day.
 */
class CalendarAlarmWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduler = AlarmScheduler(applicationContext)
        val db = AlarmDatabase.getInstance(applicationContext)
        val alarms = db.alarmDao().getEnabledAlarms()

        alarms
            .filter { it.scheduleType == ScheduleType.CALENDAR_EVENT }
            .forEach { alarm ->
                val hasEvent = CalendarHelper.hasTodayEvent(applicationContext, alarm.calendarKeywords)
                if (hasEvent) {
                    scheduler.schedule(alarm)
                } else {
                    scheduler.cancel(alarm)
                }
            }

        return Result.success()
    }
}
