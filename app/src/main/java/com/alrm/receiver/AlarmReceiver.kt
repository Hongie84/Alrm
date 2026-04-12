package com.alrm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alrm.alarm.AlarmDatabase
import com.alrm.alarm.ScheduleType
import com.alrm.scheduler.AlarmScheduler
import com.alrm.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId == -1) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AlarmDatabase.getInstance(context)
                val alarm = db.alarmDao().getAlarmById(alarmId) ?: return@launch

                if (!alarm.enabled) return@launch

                // For CALENDAR_EVENT alarms, verify a matching event exists today
                if (alarm.scheduleType == ScheduleType.CALENDAR_EVENT) {
                    val hasEvent = CalendarHelper.hasTodayEvent(context, alarm.calendarKeywords)
                    if (!hasEvent) {
                        // Re-schedule for tomorrow same time
                        AlarmScheduler(context).schedule(alarm)
                        return@launch
                    }
                }

                // Record fire time
                db.alarmDao().updateLastFiredAt(alarmId, System.currentTimeMillis())

                // Re-schedule repeating alarms immediately
                if (alarm.hasRepeat()) {
                    AlarmScheduler(context).schedule(alarm)
                } else {
                    // One-shot: disable after firing
                    db.alarmDao().setEnabled(alarmId, false)
                }

                // Start the foreground alarm service
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra(EXTRA_ALARM_ID, alarmId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
