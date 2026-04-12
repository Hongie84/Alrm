package com.alrm.alarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.alrm.scheduler.AlarmScheduler
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: AlarmRepository
    val allAlarms: LiveData<List<Alarm>>
    private val scheduler = AlarmScheduler(application)

    init {
        val dao = AlarmDatabase.getInstance(application).alarmDao()
        repo = AlarmRepository(dao)
        allAlarms = repo.allAlarms
    }

    fun insert(alarm: Alarm) = viewModelScope.launch {
        val id = repo.insert(alarm)
        if (alarm.enabled) {
            scheduler.schedule(alarm.copy(id = id.toInt()))
        }
    }

    fun update(alarm: Alarm) = viewModelScope.launch {
        repo.update(alarm)
        scheduler.cancel(alarm)
        if (alarm.enabled) {
            scheduler.schedule(alarm)
        }
    }

    fun delete(alarm: Alarm) = viewModelScope.launch {
        scheduler.cancel(alarm)
        repo.delete(alarm)
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        repo.setEnabled(alarm.id, enabled)
        if (enabled) {
            scheduler.schedule(alarm.copy(enabled = true))
        } else {
            scheduler.cancel(alarm)
        }
    }
}
