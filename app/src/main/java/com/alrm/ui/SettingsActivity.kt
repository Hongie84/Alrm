package com.alrm.ui

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.alrm.R

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = "Settings"
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }
    override fun onNavigateUp(): Boolean { finish(); return true }
}
