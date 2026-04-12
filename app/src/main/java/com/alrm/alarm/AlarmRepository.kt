package com.alrm.alarm

import androidx.lifecycle.LiveData

class AlarmRepository(private val dao: AlarmDao) {

    val allAlarms: LiveData<List<Alarm>> = dao.getAllAlarms()

    suspend fun insert(alarm: Alarm): Long = dao.insertAlarm(alarm)

    suspend fun update(alarm: Alarm) = dao.updateAlarm(alarm)

    suspend fun delete(alarm: Alarm) = dao.deleteAlarm(alarm)

    suspend fun setEnabled(id: Int, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun getById(id: Int): Alarm? = dao.getAlarmById(id)

    suspend fun getEnabledAlarms(): List<Alarm> = dao.getEnabledAlarms()

    suspend fun updateLastFiredAt(id: Int, ts: Long) = dao.updateLastFiredAt(id, ts)
}
