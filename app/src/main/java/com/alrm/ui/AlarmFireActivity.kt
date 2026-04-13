package com.alrm.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.alrm.R
import com.alrm.alarm.Alarm
import com.alrm.alarm.AlarmDatabase
import com.alrm.receiver.AlarmReceiver
import com.alrm.scheduler.AlarmScheduler
import com.alrm.service.AlarmService
import kotlinx.coroutines.*
import java.util.Locale

class AlarmFireActivity : Activity() {

    companion object {
        private const val REQUEST_PUZZLE = 42
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var alarm: Alarm? = null
    private var snoozeCount = 0

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_fire)

        val alarmId = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
        if (alarmId == -1) { finish(); return }

        scope.launch {
            alarm = withContext(Dispatchers.IO) {
                AlarmDatabase.getInstance(this@AlarmFireActivity)
                    .getAlarmDao().getAlarmById(alarmId)
            }
            alarm?.let { displayAlarm(it) }
        }

        (findViewById(R.id.btnDismiss) as Button).setOnClickListener { dismiss() }
        (findViewById(R.id.btnSnooze) as Button).setOnClickListener { initiateSnooze() }
    }

    private fun displayAlarm(alarm: Alarm) {
        (findViewById(R.id.textTime) as TextView).text =
            String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
        (findViewById(R.id.textLabel) as TextView).text =
            alarm.label.ifEmpty { getString(R.string.alarm_ringing) }
        (findViewById(R.id.textPuzzleHint) as TextView).text =
            getString(R.string.solve_puzzle_to_snooze, alarm.puzzleType.label)

        if (alarm.maxSnoozes > 0 && snoozeCount >= alarm.maxSnoozes) {
            val snoozeBtn = findViewById(R.id.btnSnooze) as Button
            snoozeBtn.isEnabled = false
            snoozeBtn.text = getString(R.string.no_more_snoozes)
        }
    }

    private fun initiateSnooze() {
        val a = alarm ?: return
        if (a.maxSnoozes > 0 && snoozeCount >= a.maxSnoozes) return
        val intent = Intent(this, PuzzleActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, a.id)
            putExtra(PuzzleActivity.EXTRA_SNOOZE_DURATION, a.snoozeDurationMinutes)
            putExtra(PuzzleActivity.EXTRA_PUZZLE_TYPE, a.puzzleType.name)
        }
        startActivityForResult(intent, REQUEST_PUZZLE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PUZZLE && resultCode == RESULT_OK) {
            val a = alarm ?: run { dismiss(); return }
            snoozeCount++
            stopAlarmService()
            // Schedule the snooze: fire again after snoozeDurationMinutes
            AlarmScheduler(this).scheduleSnooze(a)
            finish()
        }
    }

    private fun dismiss() { stopAlarmService(); finish() }

    private fun stopAlarmService() {
        startService(Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_DISMISS
        })
    }

    override fun onBackPressed() {}
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
