package com.alrm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
                val alarm = db.getAlarmDao().getAlarmById(alarmId) ?: return@launch

                if (!alarm.enabled) return@launch

                if (alarm.scheduleType == ScheduleType.CALENDAR_EVENT) {
                    val hasEvent = CalendarHelper.hasTodayEvent(context, alarm.calendarKeywords)
                    if (!hasEvent) {
                        AlarmScheduler(context).schedule(alarm)
                        return@launch
                    }
                }

                db.getAlarmDao().updateLastFiredAt(alarmId, System.currentTimeMillis())

                if (alarm.hasRepeat()) {
                    AlarmScheduler(context).schedule(alarm)
                } else {
                    db.getAlarmDao().setEnabled(alarmId, false)
                }

                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra(EXTRA_ALARM_ID, alarmId)
                }
                // API 26+: background apps must use startForegroundService().
                // Compile jar is API 23 so we invoke it via reflection at runtime.
                try {
                    context.javaClass
                        .getMethod("startForegroundService", Intent::class.java)
                        .invoke(context, serviceIntent)
                } catch (e: NoSuchMethodException) {
                    context.startService(serviceIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
