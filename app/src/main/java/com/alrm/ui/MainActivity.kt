package com.alrm.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alrm.R
import com.alrm.alarm.Alarm
import com.alrm.alarm.AlarmViewModel
import com.alrm.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: AlarmViewModel by viewModels()
    private lateinit var adapter: AlarmAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = AlarmAdapter(
            onToggle = { alarm, enabled -> viewModel.setEnabled(alarm, enabled) },
            onEdit   = { alarm -> openEditor(alarm) },
            onDelete = { alarm ->
                viewModel.delete(alarm)
                Snackbar.make(binding.root, R.string.alarm_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) { viewModel.insert(alarm) }
                    .show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.allAlarms.observe(this) { alarms ->
            adapter.submitList(alarms)
            binding.emptyView.visibility = if (alarms.isEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        binding.fab.setOnClickListener { openEditor(null) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openEditor(alarm: Alarm?) {
        val intent = Intent(this, AddEditAlarmActivity::class.java).apply {
            if (alarm != null) putExtra(AddEditAlarmActivity.EXTRA_ALARM_ID, alarm.id)
        }
        startActivity(intent)
    }
}
