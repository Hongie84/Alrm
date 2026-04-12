package com.alrm.alarm

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.Calendar

/**
 * Describes how this alarm is scheduled to repeat.
 */
enum class ScheduleType {
    ONCE,           // fires once at the set time
    DAYS_OF_WEEK,   // fires on selected weekdays
    MONTHS,         // fires on selected calendar months
    SEASONS,        // fires based on season (Spring/Summer/Autumn/Winter)
    CALENDAR_EVENT  // fires when a matching calendar event starts
}

enum class Season(val label: String, val months: Set<Int>) {
    SPRING("Spring", setOf(Calendar.MARCH, Calendar.APRIL, Calendar.MAY)),
    SUMMER("Summer", setOf(Calendar.JUNE, Calendar.JULY, Calendar.AUGUST)),
    AUTUMN("Autumn", setOf(Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER)),
    WINTER("Winter", setOf(Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY));

    fun containsMonth(month: Int): Boolean = months.contains(month)
}

enum class PuzzleType(val label: String) {
    MATH("Math problem"),
    SEQUENCE("Number sequence"),
    MEMORY("Memory match"),
    SHAKE("Shake phone")
}

@Entity(tableName = "alarms")
@TypeConverters(AlarmConverters::class)
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** Human-readable label */
    val label: String = "",

    /** Hour of day (0-23) */
    val hour: Int = 7,

    /** Minute of hour (0-59) */
    val minute: Int = 0,

    /** Whether the alarm is currently active */
    val enabled: Boolean = true,

    /** Which scheduling strategy is used */
    val scheduleType: ScheduleType = ScheduleType.ONCE,

    /**
     * DAYS_OF_WEEK: bitmask where bit 0 = Sunday … bit 6 = Saturday
     * (matches Calendar.DAY_OF_WEEK constants offset by 1)
     */
    val repeatDays: Int = 0,

    /**
     * MONTHS: bitmask where bit 0 = January … bit 11 = December
     * (matches Calendar.MONTH constants)
     */
    val repeatMonths: Int = 0,

    /**
     * SEASONS: bitmask using Season.ordinal (bit 0=SPRING, 1=SUMMER, 2=AUTUMN, 3=WINTER)
     */
    val repeatSeasons: Int = 0,

    /**
     * CALENDAR_EVENT: keyword to match against calendar event titles (comma-separated)
     */
    val calendarKeywords: String = "",

    /** Snooze duration in minutes */
    val snoozeDurationMinutes: Int = 10,

    /** Maximum number of snoozes allowed (0 = unlimited) */
    val maxSnoozes: Int = 3,

    /** Which puzzle type must be solved before snoozing */
    val puzzleType: PuzzleType = PuzzleType.MATH,

    /** Ringtone URI (empty = default) */
    val ringtoneUri: String = "",

    /** Whether to vibrate */
    val vibrate: Boolean = true,

    /** Volume 0-100 (null = device default) */
    val volume: Int = -1,

    /** Gradually increase volume over this many seconds (0 = off) */
    val crescendoSeconds: Int = 0,

    /** Timestamp of the last time this alarm fired (epoch ms) */
    val lastFiredAt: Long = 0L
) {
    /** Returns true if this alarm should fire on the given Calendar day */
    fun firesOnDay(cal: Calendar): Boolean {
        return when (scheduleType) {
            ScheduleType.ONCE -> true
            ScheduleType.DAYS_OF_WEEK -> {
                val dayBit = 1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1)
                (repeatDays and dayBit) != 0
            }
            ScheduleType.MONTHS -> {
                val monthBit = 1 shl cal.get(Calendar.MONTH)
                (repeatMonths and monthBit) != 0
            }
            ScheduleType.SEASONS -> {
                val month = cal.get(Calendar.MONTH)
                Season.entries.any { season ->
                    val seasonBit = 1 shl season.ordinal
                    (repeatSeasons and seasonBit) != 0 && season.containsMonth(month)
                }
            }
            ScheduleType.CALENDAR_EVENT -> true // handled by scheduler separately
        }
    }

    fun hasRepeat(): Boolean = scheduleType != ScheduleType.ONCE

    /** Days-of-week helper: is the given Calendar.DAY_OF_WEEK selected? */
    fun isDaySelected(dayOfWeek: Int): Boolean =
        (repeatDays and (1 shl (dayOfWeek - 1))) != 0

    fun withDayToggled(dayOfWeek: Int): Alarm {
        val bit = 1 shl (dayOfWeek - 1)
        return copy(repeatDays = repeatDays xor bit)
    }

    fun isMonthSelected(month: Int): Boolean =
        (repeatMonths and (1 shl month)) != 0

    fun withMonthToggled(month: Int): Alarm {
        val bit = 1 shl month
        return copy(repeatMonths = repeatMonths xor bit)
    }

    fun isSeasonSelected(season: Season): Boolean =
        (repeatSeasons and (1 shl season.ordinal)) != 0

    fun withSeasonToggled(season: Season): Alarm {
        val bit = 1 shl season.ordinal
        return copy(repeatSeasons = repeatSeasons xor bit)
    }
}

class AlarmConverters {
    @TypeConverter fun fromScheduleType(v: ScheduleType): String = v.name
    @TypeConverter fun toScheduleType(v: String): ScheduleType = ScheduleType.valueOf(v)
    @TypeConverter fun fromPuzzleType(v: PuzzleType): String = v.name
    @TypeConverter fun toPuzzleType(v: String): PuzzleType = PuzzleType.valueOf(v)
}
