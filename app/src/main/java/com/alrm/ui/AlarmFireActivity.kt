package com.alrm.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.alrm.R
import com.alrm.alarm.AlarmDatabase
import com.alrm.alarm.Alarm
import com.alrm.alarm.PuzzleType
import com.alrm.databinding.ActivityAlarmFireBinding
import com.alrm.receiver.AlarmReceiver
import com.alrm.service.AlarmService
import kotlinx.coroutines.launch
import java.util.Locale

class AlarmFireActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmFireBinding
    private var alarm: Alarm? = null
    private var snoozeCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Show over lock screen and turn on screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmFireBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val alarmId = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
        if (alarmId == -1) {
            finish()
            return
        }

        lifecycleScope.launch {
            alarm = AlarmDatabase.getInstance(this@AlarmFireActivity)
                .alarmDao().getAlarmById(alarmId)
            alarm?.let { displayAlarm(it) }
        }

        binding.btnDismiss.setOnClickListener { dismiss() }
        binding.btnSnooze.setOnClickListener { initiateSnooze() }
    }

    private fun displayAlarm(alarm: Alarm) {
        binding.textTime.text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
        binding.textLabel.text = alarm.label.ifEmpty { getString(R.string.alarm_ringing) }
        binding.textPuzzleHint.text = getString(R.string.solve_puzzle_to_snooze, alarm.puzzleType.label)

        val maxSnoozes = alarm.maxSnoozes
        if (maxSnoozes > 0 && snoozeCount >= maxSnoozes) {
            binding.btnSnooze.isEnabled = false
            binding.btnSnooze.text = getString(R.string.no_more_snoozes)
        }
    }

    private fun initiateSnooze() {
        val a = alarm ?: return
        val max = a.maxSnoozes
        if (max > 0 && snoozeCount >= max) return

        // Launch puzzle; snooze only happens after puzzle is solved
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
            snoozeCount++
            stopAlarmService()
            finish()
        }
    }

    private fun dismiss() {
        stopAlarmService()
        finish()
    }

    private fun stopAlarmService() {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_DISMISS
        }
        startService(stopIntent)
    }

    override fun onBackPressed() {
        // Prevent back-press dismissing without solving
    }

    companion object {
        private const val REQUEST_PUZZLE = 42
    }
}
