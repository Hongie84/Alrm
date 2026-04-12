package com.alrm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alrm.alarm.AlarmDatabase
import com.alrm.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) return

        val pendingResult = goAsync()
        val scheduler = AlarmScheduler(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = AlarmDatabase.getInstance(context).alarmDao().getEnabledAlarms()
                alarms.forEach { scheduler.schedule(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
