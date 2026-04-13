package com.alrm.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.alrm.alarm.Alarm
import com.alrm.alarm.ScheduleType
import com.alrm.receiver.AlarmReceiver
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm) {
        val triggerAtMillis = nextTriggerTime(alarm) ?: return
        val intent = buildIntent(alarm)
        val pi = PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, pi), pi
        )
    }

    fun cancel(alarm: Alarm) {
        val intent = buildIntent(alarm)
        val pi = PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    fun nextTriggerTime(alarm: Alarm): Long? {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (candidate.timeInMillis <= now.timeInMillis) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }

        return when (alarm.scheduleType) {
            ScheduleType.ONCE -> candidate.timeInMillis

            ScheduleType.DAYS_OF_WEEK -> {
                if (alarm.repeatDays == 0) return null
                repeat(7) {
                    if (alarm.firesOnDay(candidate)) return candidate.timeInMillis
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                }
                null
            }

            ScheduleType.MONTHS -> {
                if (alarm.repeatMonths == 0) return null
                repeat(366) {
                    if (alarm.firesOnDay(candidate)) return candidate.timeInMillis
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                }
                null
            }

            ScheduleType.SEASONS -> {
                if (alarm.repeatSeasons == 0) return null
                repeat(366) {
                    if (alarm.firesOnDay(candidate)) return candidate.timeInMillis
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                }
                null
            }

            ScheduleType.CALENDAR_EVENT -> candidate.timeInMillis
        }
    }

    private fun buildIntent(alarm: Alarm): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            action = "com.alrm.ALARM_TRIGGER"
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
        }
}
