package com.alrm.receiver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar

object CalendarHelper {

    fun hasTodayEvent(context: Context, keywords: String): Boolean {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) return false

        val keywordList = keywords.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        if (keywordList.isEmpty()) return true

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfDay = startOfDay + 24 * 60 * 60 * 1000L - 1

        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(CalendarContract.Events.TITLE)
        val selection = "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?)" +
                " OR (${CalendarContract.Events.DTEND} >= ? AND ${CalendarContract.Events.DTEND} <= ?)"
        val selectionArgs = arrayOf(
            startOfDay.toString(), endOfDay.toString(),
            startOfDay.toString(), endOfDay.toString()
        )

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0)?.lowercase() ?: continue
                if (keywordList.any { title.contains(it) }) return true
            }
        }
        return false
    }

    fun getTodayEventTitles(context: Context): List<String> {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000L - 1

        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(CalendarContract.Events.TITLE)
        val selection = "${CalendarContract.Events.DTSTART} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(startOfDay.toString(), endOfDay.toString())

        val titles = mutableListOf<String>()
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { titles.add(it) }
            }
        }
        return titles
    }
}
