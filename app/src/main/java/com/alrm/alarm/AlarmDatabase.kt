package com.alrm.alarm

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AlarmDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION) {

    companion object {
        private const val DATABASE_NAME = "alrm.db"
        private const val VERSION = 1

        @Volatile private var INSTANCE: AlarmDatabase? = null
        fun getInstance(context: Context): AlarmDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlarmDatabase(context.applicationContext).also { INSTANCE = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE alarms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                label TEXT NOT NULL DEFAULT '',
                hour INTEGER NOT NULL DEFAULT 7,
                minute INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                scheduleType TEXT NOT NULL DEFAULT 'ONCE',
                repeatDays INTEGER NOT NULL DEFAULT 0,
                repeatMonths INTEGER NOT NULL DEFAULT 0,
                repeatSeasons INTEGER NOT NULL DEFAULT 0,
                calendarKeywords TEXT NOT NULL DEFAULT '',
                snoozeDurationMinutes INTEGER NOT NULL DEFAULT 10,
                maxSnoozes INTEGER NOT NULL DEFAULT 3,
                puzzleType TEXT NOT NULL DEFAULT 'MATH',
                ringtoneUri TEXT NOT NULL DEFAULT '',
                vibrate INTEGER NOT NULL DEFAULT 1,
                volume INTEGER NOT NULL DEFAULT -1,
                lastFiredAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun getAlarmDao(): AlarmDao = AlarmDao(writableDatabase)
}

class AlarmDao(private val db: SQLiteDatabase) {

    fun getAllAlarms(): List<Alarm> {
        val list = mutableListOf<Alarm>()
        db.rawQuery("SELECT * FROM alarms ORDER BY hour, minute", null).use { c ->
            while (c.moveToNext()) list.add(c.toAlarm())
        }
        return list
    }

    fun getEnabledAlarms(): List<Alarm> {
        val list = mutableListOf<Alarm>()
        db.rawQuery("SELECT * FROM alarms WHERE enabled=1", null).use { c ->
            while (c.moveToNext()) list.add(c.toAlarm())
        }
        return list
    }

    fun getAlarmById(id: Int): Alarm? {
        db.rawQuery("SELECT * FROM alarms WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) return c.toAlarm()
        }
        return null
    }

    fun insert(alarm: Alarm): Long = db.insert("alarms", null, alarm.toValues())

    fun update(alarm: Alarm) {
        db.update("alarms", alarm.toValues(), "id=?", arrayOf(alarm.id.toString()))
    }

    fun delete(alarmId: Int) {
        db.delete("alarms", "id=?", arrayOf(alarmId.toString()))
    }

    fun setEnabled(id: Int, enabled: Boolean) {
        db.execSQL("UPDATE alarms SET enabled=? WHERE id=?",
            arrayOf(if (enabled) 1 else 0, id))
    }

    fun updateLastFiredAt(id: Int, ts: Long) {
        db.execSQL("UPDATE alarms SET lastFiredAt=? WHERE id=?", arrayOf(ts, id))
    }

    private fun android.database.Cursor.toAlarm() = Alarm(
        id                   = getInt(getColumnIndexOrThrow("id")),
        label                = getString(getColumnIndexOrThrow("label")) ?: "",
        hour                 = getInt(getColumnIndexOrThrow("hour")),
        minute               = getInt(getColumnIndexOrThrow("minute")),
        enabled              = getInt(getColumnIndexOrThrow("enabled")) != 0,
        scheduleType         = ScheduleType.valueOf(getString(getColumnIndexOrThrow("scheduleType"))),
        repeatDays           = getInt(getColumnIndexOrThrow("repeatDays")),
        repeatMonths         = getInt(getColumnIndexOrThrow("repeatMonths")),
        repeatSeasons        = getInt(getColumnIndexOrThrow("repeatSeasons")),
        calendarKeywords     = getString(getColumnIndexOrThrow("calendarKeywords")) ?: "",
        snoozeDurationMinutes = getInt(getColumnIndexOrThrow("snoozeDurationMinutes")),
        maxSnoozes           = getInt(getColumnIndexOrThrow("maxSnoozes")),
        puzzleType           = PuzzleType.valueOf(getString(getColumnIndexOrThrow("puzzleType"))),
        ringtoneUri          = getString(getColumnIndexOrThrow("ringtoneUri")) ?: "",
        vibrate              = getInt(getColumnIndexOrThrow("vibrate")) != 0,
        volume               = getInt(getColumnIndexOrThrow("volume")),
        lastFiredAt          = getLong(getColumnIndexOrThrow("lastFiredAt"))
    )

    private fun Alarm.toValues() = ContentValues().apply {
        if (id != 0) put("id", id)
        put("label", label)
        put("hour", hour)
        put("minute", minute)
        put("enabled", if (enabled) 1 else 0)
        put("scheduleType", scheduleType.name)
        put("repeatDays", repeatDays)
        put("repeatMonths", repeatMonths)
        put("repeatSeasons", repeatSeasons)
        put("calendarKeywords", calendarKeywords)
        put("snoozeDurationMinutes", snoozeDurationMinutes)
        put("maxSnoozes", maxSnoozes)
        put("puzzleType", puzzleType.name)
        put("ringtoneUri", ringtoneUri)
        put("vibrate", if (vibrate) 1 else 0)
        put("volume", volume)
        put("lastFiredAt", lastFiredAt)
    }
}
