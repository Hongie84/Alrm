package com.alrm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alrm.R
import com.alrm.alarm.Alarm
import com.alrm.alarm.AlarmDatabase
import com.alrm.alarm.AlarmViewModel
import com.alrm.alarm.PuzzleType
import com.alrm.alarm.ScheduleType
import com.alrm.alarm.Season
import com.alrm.databinding.ActivityAddEditAlarmBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class AddEditAlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }

    private lateinit var binding: ActivityAddEditAlarmBinding
    private val viewModel: AlarmViewModel by viewModels()
    private var existingAlarm: Alarm? = null
    private var currentAlarm = Alarm()

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.calendar_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupSpinners()
        setupDayButtons()
        setupMonthButtons()
        setupSeasonButtons()

        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId != -1) {
            supportActionBar?.title = getString(R.string.edit_alarm)
            lifecycleScope.launch {
                val alarm = AlarmDatabase.getInstance(this@AddEditAlarmActivity)
                    .alarmDao().getAlarmById(alarmId)
                if (alarm != null) {
                    existingAlarm = alarm
                    currentAlarm = alarm
                    populateUi(alarm)
                }
            }
        } else {
            supportActionBar?.title = getString(R.string.add_alarm)
        }

        binding.btnSave.setOnClickListener { saveAlarm() }
    }

    private fun setupSpinners() {
        // Schedule type
        val scheduleTypes = ScheduleType.entries.map { it.name.replace('_', ' ') }
        val scheduleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, scheduleTypes)
        scheduleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerScheduleType.adapter = scheduleAdapter
        binding.spinnerScheduleType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val type = ScheduleType.entries[pos]
                currentAlarm = currentAlarm.copy(scheduleType = type)
                updateScheduleVisibility(type)
                if (type == ScheduleType.CALENDAR_EVENT) {
                    requestCalendarPermission()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Puzzle type
        val puzzleLabels = PuzzleType.entries.map { it.label }
        val puzzleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, puzzleLabels)
        puzzleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPuzzleType.adapter = puzzleAdapter
        binding.spinnerPuzzleType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                currentAlarm = currentAlarm.copy(puzzleType = PuzzleType.entries[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupDayButtons() {
        val days = listOf(
            binding.btnSun to Calendar.SUNDAY,
            binding.btnMon to Calendar.MONDAY,
            binding.btnTue to Calendar.TUESDAY,
            binding.btnWed to Calendar.WEDNESDAY,
            binding.btnThu to Calendar.THURSDAY,
            binding.btnFri to Calendar.FRIDAY,
            binding.btnSat to Calendar.SATURDAY
        )
        days.forEach { (btn, day) ->
            btn.setOnClickListener {
                currentAlarm = currentAlarm.withDayToggled(day)
                btn.isSelected = currentAlarm.isDaySelected(day)
            }
        }
    }

    private fun setupMonthButtons() {
        val months = listOf(
            binding.btnJan to Calendar.JANUARY,
            binding.btnFeb to Calendar.FEBRUARY,
            binding.btnMar to Calendar.MARCH,
            binding.btnApr to Calendar.APRIL,
            binding.btnMay to Calendar.MAY,
            binding.btnJun to Calendar.JUNE,
            binding.btnJul to Calendar.JULY,
            binding.btnAug to Calendar.AUGUST,
            binding.btnSep to Calendar.SEPTEMBER,
            binding.btnOct to Calendar.OCTOBER,
            binding.btnNov to Calendar.NOVEMBER,
            binding.btnDec to Calendar.DECEMBER
        )
        months.forEach { (btn, month) ->
            btn.setOnClickListener {
                currentAlarm = currentAlarm.withMonthToggled(month)
                btn.isSelected = currentAlarm.isMonthSelected(month)
            }
        }
    }

    private fun setupSeasonButtons() {
        val seasons = listOf(
            binding.btnSpring to Season.SPRING,
            binding.btnSummer to Season.SUMMER,
            binding.btnAutumn to Season.AUTUMN,
            binding.btnWinter to Season.WINTER
        )
        seasons.forEach { (btn, season) ->
            btn.setOnClickListener {
                currentAlarm = currentAlarm.withSeasonToggled(season)
                btn.isSelected = currentAlarm.isSeasonSelected(season)
            }
        }
    }

    private fun updateScheduleVisibility(type: ScheduleType) {
        binding.layoutDays.visibility    = if (type == ScheduleType.DAYS_OF_WEEK) View.VISIBLE else View.GONE
        binding.layoutMonths.visibility  = if (type == ScheduleType.MONTHS) View.VISIBLE else View.GONE
        binding.layoutSeasons.visibility = if (type == ScheduleType.SEASONS) View.VISIBLE else View.GONE
        binding.layoutCalendar.visibility = if (type == ScheduleType.CALENDAR_EVENT) View.VISIBLE else View.GONE
    }

    private fun populateUi(alarm: Alarm) {
        binding.timePicker.hour   = alarm.hour
        binding.timePicker.minute = alarm.minute
        binding.editLabel.setText(alarm.label)
        binding.spinnerScheduleType.setSelection(alarm.scheduleType.ordinal)
        binding.spinnerPuzzleType.setSelection(alarm.puzzleType.ordinal)
        binding.switchVibrate.isChecked = alarm.vibrate
        binding.seekbarVolume.progress = if (alarm.volume >= 0) alarm.volume else 80
        binding.editCalendarKeywords.setText(alarm.calendarKeywords)
        binding.sliderSnoozeDuration.value = alarm.snoozeDurationMinutes.toFloat()
        binding.sliderMaxSnoozes.value = alarm.maxSnoozes.toFloat()

        // Day buttons
        listOf(
            binding.btnSun to Calendar.SUNDAY,
            binding.btnMon to Calendar.MONDAY,
            binding.btnTue to Calendar.TUESDAY,
            binding.btnWed to Calendar.WEDNESDAY,
            binding.btnThu to Calendar.THURSDAY,
            binding.btnFri to Calendar.FRIDAY,
            binding.btnSat to Calendar.SATURDAY
        ).forEach { (btn, day) -> btn.isSelected = alarm.isDaySelected(day) }

        // Month buttons
        listOf(
            binding.btnJan to Calendar.JANUARY, binding.btnFeb to Calendar.FEBRUARY,
            binding.btnMar to Calendar.MARCH,   binding.btnApr to Calendar.APRIL,
            binding.btnMay to Calendar.MAY,     binding.btnJun to Calendar.JUNE,
            binding.btnJul to Calendar.JULY,    binding.btnAug to Calendar.AUGUST,
            binding.btnSep to Calendar.SEPTEMBER, binding.btnOct to Calendar.OCTOBER,
            binding.btnNov to Calendar.NOVEMBER, binding.btnDec to Calendar.DECEMBER
        ).forEach { (btn, month) -> btn.isSelected = alarm.isMonthSelected(month) }

        // Season buttons
        listOf(
            binding.btnSpring to Season.SPRING, binding.btnSummer to Season.SUMMER,
            binding.btnAutumn to Season.AUTUMN, binding.btnWinter to Season.WINTER
        ).forEach { (btn, season) -> btn.isSelected = alarm.isSeasonSelected(season) }

        updateScheduleVisibility(alarm.scheduleType)
    }

    private fun saveAlarm() {
        val hour   = binding.timePicker.hour
        val minute = binding.timePicker.minute
        val label  = binding.editLabel.text?.toString()?.trim() ?: ""
        val vibrate = binding.switchVibrate.isChecked
        val volume  = binding.seekbarVolume.progress
        val calKeywords = binding.editCalendarKeywords.text?.toString()?.trim() ?: ""
        val snoozeDuration = binding.sliderSnoozeDuration.value.toInt()
        val maxSnoozes = binding.sliderMaxSnoozes.value.toInt()

        val alarm = currentAlarm.copy(
            id                   = existingAlarm?.id ?: 0,
            hour                 = hour,
            minute               = minute,
            label                = label,
            vibrate              = vibrate,
            volume               = volume,
            calendarKeywords     = calKeywords,
            snoozeDurationMinutes = snoozeDuration,
            maxSnoozes           = maxSnoozes,
            enabled              = true
        )

        if (existingAlarm != null) {
            viewModel.update(alarm)
        } else {
            viewModel.insert(alarm)
        }
        finish()
    }

    private fun requestCalendarPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
