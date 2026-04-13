package com.alrm.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.alrm.R
import com.alrm.alarm.Alarm
import com.alrm.alarm.AlarmDatabase
import com.alrm.scheduler.AlarmScheduler
import kotlinx.coroutines.*
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var alarms: List<Alarm> = emptyList()
    private lateinit var scheduler: AlarmScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        scheduler = AlarmScheduler(this)

        listView = findViewById(R.id.listView) as ListView
        emptyView = findViewById(R.id.emptyView) as TextView

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            openEditor(alarms[pos])
        }
        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            confirmDelete(alarms[pos])
            true
        }

        (findViewById(R.id.fab) as View).setOnClickListener { openEditor(null) }
    }

    override fun onResume() {
        super.onResume()
        loadAlarms()
    }

    private fun loadAlarms() {
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                AlarmDatabase.getInstance(this@MainActivity).getAlarmDao().getAllAlarms()
            }
            alarms = data
            val labels = data.map { a ->
                String.format(Locale.getDefault(), "%02d:%02d  %s  [%s]",
                    a.hour, a.minute,
                    a.label.ifEmpty { "Alarm" },
                    if (a.enabled) "ON" else "OFF")
            }
            val adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_list_item_1, labels)
            listView.adapter = adapter
            emptyView.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmDelete(alarm: Alarm) {
        AlertDialog.Builder(this)
            .setTitle("Delete alarm")
            .setMessage("Delete \"${alarm.label.ifEmpty { "Alarm" }}\"?")
            .setPositiveButton("Delete") { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        scheduler.cancel(alarm)
                        AlarmDatabase.getInstance(this@MainActivity)
                            .getAlarmDao().delete(alarm.id)
                    }
                    loadAlarms()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openEditor(alarm: Alarm?) {
        val intent = Intent(this, AddEditAlarmActivity::class.java)
        if (alarm != null) intent.putExtra(AddEditAlarmActivity.EXTRA_ALARM_ID, alarm.id)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Settings")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) startActivity(Intent(this, SettingsActivity::class.java))
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
