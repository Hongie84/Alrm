package com.alrm.alarm

import java.util.Calendar

enum class ScheduleType {
    ONCE,
    DAYS_OF_WEEK,
    MONTHS,
    SEASONS,
    CALENDAR_EVENT
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

data class Alarm(
    val id: Int = 0,
    val label: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    val scheduleType: ScheduleType = ScheduleType.ONCE,
    val repeatDays: Int = 0,
    val repeatMonths: Int = 0,
    val repeatSeasons: Int = 0,
    val calendarKeywords: String = "",
    val snoozeDurationMinutes: Int = 10,
    val maxSnoozes: Int = 3,
    val puzzleType: PuzzleType = PuzzleType.MATH,
    val ringtoneUri: String = "",
    val vibrate: Boolean = true,
    val volume: Int = -1,
    val lastFiredAt: Long = 0L
) {
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
            ScheduleType.CALENDAR_EVENT -> true
        }
    }

    fun hasRepeat(): Boolean = scheduleType != ScheduleType.ONCE

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
