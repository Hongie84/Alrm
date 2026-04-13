package com.alrm.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import com.alrm.R
import com.alrm.alarm.*
import com.alrm.scheduler.AlarmScheduler
import kotlinx.coroutines.*
import java.util.Calendar

class AddEditAlarmActivity : Activity() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val RC_CALENDAR = 101
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var existingId: Int = -1
    private var currentAlarm = Alarm()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_alarm)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId != -1) {
            title = "Edit Alarm"
            existingId = alarmId
            scope.launch {
                val alarm = withContext(Dispatchers.IO) {
                    AlarmDatabase.getInstance(this@AddEditAlarmActivity)
                        .getAlarmDao().getAlarmById(alarmId)
                }
                if (alarm != null) {
                    currentAlarm = alarm
                    populateUi(alarm)
                }
            }
        } else {
            title = "Add Alarm"
        }

        setupScheduleTypeSpinner()
        setupPuzzleTypeSpinner()
        setupDayButtons()
        setupMonthButtons()
        setupSeasonButtons()

        (findViewById(R.id.btnSave) as Button).setOnClickListener { saveAlarm() }
    }

    private fun tp(): TimePicker = findViewById(R.id.timePicker) as TimePicker
    private fun editLabel(): EditText = findViewById(R.id.editLabel) as EditText
    private fun spinSchedule(): Spinner = findViewById(R.id.spinnerScheduleType) as Spinner
    private fun spinPuzzle(): Spinner = findViewById(R.id.spinnerPuzzleType) as Spinner
    private fun switchVibrate(): CheckBox = findViewById(R.id.switchVibrate) as CheckBox
    private fun seekVol(): SeekBar = findViewById(R.id.seekbarVolume) as SeekBar
    private fun editKeywords(): EditText = findViewById(R.id.editCalendarKeywords) as EditText
    private fun sliderSnooze(): SeekBar = findViewById(R.id.sliderSnoozeDuration) as SeekBar
    private fun sliderMax(): SeekBar = findViewById(R.id.sliderMaxSnoozes) as SeekBar

    private fun setupScheduleTypeSpinner() {
        val types = ScheduleType.entries.map { it.name.replace('_', ' ') }
        spinSchedule().adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinSchedule().onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val type = ScheduleType.entries[pos]
                currentAlarm = currentAlarm.copy(scheduleType = type)
                updateScheduleVisibility(type)
                if (type == ScheduleType.CALENDAR_EVENT) requestCalendarPermission()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    private fun setupPuzzleTypeSpinner() {
        val labels = PuzzleType.entries.map { it.label }
        spinPuzzle().adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinPuzzle().onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                currentAlarm = currentAlarm.copy(puzzleType = PuzzleType.entries[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    private fun setupDayButtons() {
        val dayMap = listOf(
            R.id.btnSun to Calendar.SUNDAY, R.id.btnMon to Calendar.MONDAY,
            R.id.btnTue to Calendar.TUESDAY, R.id.btnWed to Calendar.WEDNESDAY,
            R.id.btnThu to Calendar.THURSDAY, R.id.btnFri to Calendar.FRIDAY,
            R.id.btnSat to Calendar.SATURDAY
        )
        for (pair in dayMap) {
            val id = pair.first; val day = pair.second
            (findViewById(id) as Button).setOnClickListener {
                currentAlarm = currentAlarm.withDayToggled(day)
                (it as Button).isSelected = currentAlarm.isDaySelected(day)
            }
        }
    }

    private fun setupMonthButtons() {
        val monthMap = listOf(
            R.id.btnJan to Calendar.JANUARY, R.id.btnFeb to Calendar.FEBRUARY,
            R.id.btnMar to Calendar.MARCH,   R.id.btnApr to Calendar.APRIL,
            R.id.btnMay to Calendar.MAY,     R.id.btnJun to Calendar.JUNE,
            R.id.btnJul to Calendar.JULY,    R.id.btnAug to Calendar.AUGUST,
            R.id.btnSep to Calendar.SEPTEMBER, R.id.btnOct to Calendar.OCTOBER,
            R.id.btnNov to Calendar.NOVEMBER, R.id.btnDec to Calendar.DECEMBER
        )
        for (pair in monthMap) {
            val id = pair.first; val month = pair.second
            (findViewById(id) as Button).setOnClickListener {
                currentAlarm = currentAlarm.withMonthToggled(month)
                (it as Button).isSelected = currentAlarm.isMonthSelected(month)
            }
        }
    }

    private fun setupSeasonButtons() {
        val seasonMap = listOf(
            R.id.btnSpring to Season.SPRING, R.id.btnSummer to Season.SUMMER,
            R.id.btnAutumn to Season.AUTUMN, R.id.btnWinter to Season.WINTER
        )
        for (pair in seasonMap) {
            val id = pair.first; val season = pair.second
            (findViewById(id) as Button).setOnClickListener {
                currentAlarm = currentAlarm.withSeasonToggled(season)
                (it as Button).isSelected = currentAlarm.isSeasonSelected(season)
            }
        }
    }

    private fun updateScheduleVisibility(type: ScheduleType) {
        fun vis(id: Int, show: Boolean) {
            findViewById(id).visibility = if (show) View.VISIBLE else View.GONE
        }
        vis(R.id.layoutDays,    type == ScheduleType.DAYS_OF_WEEK)
        vis(R.id.layoutMonths,  type == ScheduleType.MONTHS)
        vis(R.id.layoutSeasons, type == ScheduleType.SEASONS)
        vis(R.id.layoutCalendar, type == ScheduleType.CALENDAR_EVENT)
    }

    private fun populateUi(alarm: Alarm) {
        tp().currentHour = alarm.hour
        tp().currentMinute = alarm.minute
        editLabel().setText(alarm.label)
        spinSchedule().setSelection(alarm.scheduleType.ordinal)
        spinPuzzle().setSelection(alarm.puzzleType.ordinal)
        switchVibrate().isChecked = alarm.vibrate
        seekVol().progress = if (alarm.volume >= 0) alarm.volume else 80
        editKeywords().setText(alarm.calendarKeywords)
        sliderSnooze().progress = alarm.snoozeDurationMinutes - 1
        sliderMax().progress = alarm.maxSnoozes

        val dayMap = listOf(R.id.btnSun to Calendar.SUNDAY, R.id.btnMon to Calendar.MONDAY,
            R.id.btnTue to Calendar.TUESDAY, R.id.btnWed to Calendar.WEDNESDAY,
            R.id.btnThu to Calendar.THURSDAY, R.id.btnFri to Calendar.FRIDAY,
            R.id.btnSat to Calendar.SATURDAY)
        for (pair in dayMap) {
            (findViewById(pair.first) as Button).isSelected = alarm.isDaySelected(pair.second)
        }

        val monthMap = listOf(R.id.btnJan to Calendar.JANUARY, R.id.btnFeb to Calendar.FEBRUARY,
            R.id.btnMar to Calendar.MARCH, R.id.btnApr to Calendar.APRIL,
            R.id.btnMay to Calendar.MAY, R.id.btnJun to Calendar.JUNE,
            R.id.btnJul to Calendar.JULY, R.id.btnAug to Calendar.AUGUST,
            R.id.btnSep to Calendar.SEPTEMBER, R.id.btnOct to Calendar.OCTOBER,
            R.id.btnNov to Calendar.NOVEMBER, R.id.btnDec to Calendar.DECEMBER)
        for (pair in monthMap) {
            (findViewById(pair.first) as Button).isSelected = alarm.isMonthSelected(pair.second)
        }

        val seasonMap = listOf(R.id.btnSpring to Season.SPRING, R.id.btnSummer to Season.SUMMER,
            R.id.btnAutumn to Season.AUTUMN, R.id.btnWinter to Season.WINTER)
        for (pair in seasonMap) {
            (findViewById(pair.first) as Button).isSelected = alarm.isSeasonSelected(pair.second)
        }

        updateScheduleVisibility(alarm.scheduleType)
    }

    private fun saveAlarm() {
        val alarm = currentAlarm.copy(
            id                    = if (existingId != -1) existingId else 0,
            hour                  = tp().currentHour,
            minute                = tp().currentMinute,
            label                 = editLabel().text.toString().trim(),
            vibrate               = switchVibrate().isChecked,
            volume                = seekVol().progress,
            calendarKeywords      = editKeywords().text.toString().trim(),
            snoozeDurationMinutes = sliderSnooze().progress + 1,
            maxSnoozes            = sliderMax().progress,
            enabled               = true
        )
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = AlarmDatabase.getInstance(this@AddEditAlarmActivity)
                val dao = db.getAlarmDao()
                val scheduler = AlarmScheduler(this@AddEditAlarmActivity)
                if (existingId != -1) {
                    scheduler.cancel(alarm)
                    dao.update(alarm)
                } else {
                    val newId = dao.insert(alarm).toInt()
                    if (alarm.enabled) scheduler.schedule(alarm.copy(id = newId))
                    return@withContext
                }
                if (alarm.enabled) scheduler.schedule(alarm)
            }
            finish()
        }
    }

    private fun requestCalendarPermission() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), RC_CALENDAR)
        }
    }

    override fun onNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
