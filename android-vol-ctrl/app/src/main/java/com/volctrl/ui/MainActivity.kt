package com.volctrl.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.volctrl.R
import com.volctrl.audio.StreamType
import com.volctrl.audio.VolumeController
import com.volctrl.preset.Preset
import com.volctrl.preset.PresetStore

class MainActivity : Activity() {

    private lateinit var controller: VolumeController
    private lateinit var presetStore: PresetStore
    private lateinit var streamContainer: LinearLayout
    private lateinit var ringerNormal: Button
    private lateinit var ringerVibrate: Button
    private lateinit var ringerSilent: Button
    private lateinit var dndBanner: TextView

    private val rows = LinkedHashMap<StreamType, StreamRow>()

    /** Volume to restore when a stream is un-muted, keyed by stream. */
    private val volumeBeforeMute = HashMap<StreamType, Int>()

    /** Set while a slider is under the finger, so system updates don't fight the drag. */
    private var userIsDragging = false

    private val handler = Handler(Looper.getMainLooper())

    /** Volumes live in Settings.System, so this catches hardware-key and other app changes. */
    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            refreshFromSystem()
        }
    }

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshRingerMode()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        controller = VolumeController(this)
        presetStore = PresetStore(this)

        streamContainer = findViewById(R.id.streamContainer) as LinearLayout
        ringerNormal = findViewById(R.id.buttonRingerNormal) as Button
        ringerVibrate = findViewById(R.id.buttonRingerVibrate) as Button
        ringerSilent = findViewById(R.id.buttonRingerSilent) as Button
        dndBanner = findViewById(R.id.textDndBanner) as TextView

        buildStreamRows()

        ringerNormal.setOnClickListener { applyRingerMode(AudioManager.RINGER_MODE_NORMAL) }
        ringerVibrate.setOnClickListener { applyRingerMode(AudioManager.RINGER_MODE_VIBRATE) }
        ringerSilent.setOnClickListener { applyRingerMode(AudioManager.RINGER_MODE_SILENT) }
        dndBanner.setOnClickListener { openDoNotDisturbSettings() }
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
        registerReceiver(ringerReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
        refreshFromSystem()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(volumeObserver)
        unregisterReceiver(ringerReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save_preset -> promptSavePreset()
            R.id.action_presets -> startActivity(Intent(this, PresetsActivity::class.java))
            R.id.action_dnd_access -> openDoNotDisturbSettings()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun buildStreamRows() {
        val inflater = LayoutInflater.from(this)
        StreamType.values().forEach { stream ->
            val view = inflater.inflate(R.layout.row_stream, streamContainer, false)
            val row = StreamRow(
                icon = view.findViewById(R.id.imageStreamIcon) as ImageView,
                label = view.findViewById(R.id.textStreamLabel) as TextView,
                value = view.findViewById(R.id.textStreamValue) as TextView,
                seekBar = view.findViewById(R.id.seekStream) as SeekBar,
                mute = view.findViewById(R.id.buttonMute) as ImageView
            )
            row.icon.setImageResource(stream.iconRes)
            row.label.setText(stream.labelRes)
            row.seekBar.max = controller.maxVolume(stream)
            row.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) applyVolume(stream, progress)
                }

                override fun onStartTrackingTouch(bar: SeekBar) {
                    userIsDragging = true
                }

                override fun onStopTrackingTouch(bar: SeekBar) {
                    userIsDragging = false
                }
            })
            row.mute.setOnClickListener { toggleMute(stream) }
            rows[stream] = row
            streamContainer.addView(view)
        }
    }

    private fun applyVolume(stream: StreamType, value: Int) {
        if (controller.setVolume(stream, value)) {
            updateRow(stream, value)
        } else {
            reportChangeDenied()
            refreshFromSystem()
        }
    }

    private fun toggleMute(stream: StreamType) {
        val current = controller.volume(stream)
        val target = if (current > 0) {
            volumeBeforeMute[stream] = current
            0
        } else {
            volumeBeforeMute[stream] ?: (controller.maxVolume(stream) / 2).coerceAtLeast(1)
        }
        applyVolume(stream, target)
        refreshFromSystem()
    }

    private fun applyRingerMode(mode: Int) {
        if (controller.setRingerMode(mode)) {
            refreshFromSystem()
        } else {
            reportChangeDenied()
        }
    }

    private fun refreshFromSystem() {
        if (userIsDragging) return
        rows.keys.forEach { stream ->
            val current = controller.volume(stream)
            rows[stream]?.seekBar?.max = controller.maxVolume(stream)
            rows[stream]?.seekBar?.progress = current
            updateRow(stream, current)
        }
        refreshRingerMode()
        refreshDndBanner()
    }

    private fun updateRow(stream: StreamType, value: Int) {
        val row = rows[stream] ?: return
        row.value.text = getString(R.string.volume_value, value, controller.maxVolume(stream))
        row.mute.setImageResource(if (value == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
        row.mute.contentDescription =
            getString(if (value == 0) R.string.action_unmute else R.string.action_mute)
    }

    private fun refreshRingerMode() {
        val mode = controller.ringerMode()
        setRingerButtonActive(ringerNormal, mode == AudioManager.RINGER_MODE_NORMAL)
        setRingerButtonActive(ringerVibrate, mode == AudioManager.RINGER_MODE_VIBRATE)
        setRingerButtonActive(ringerSilent, mode == AudioManager.RINGER_MODE_SILENT)
    }

    private fun setRingerButtonActive(button: Button, active: Boolean) {
        button.alpha = if (active) 1f else 0.45f
    }

    private fun refreshDndBanner() {
        dndBanner.visibility = if (controller.hasDoNotDisturbAccess()) View.GONE else View.VISIBLE
    }

    private fun reportChangeDenied() {
        Toast.makeText(this, R.string.error_needs_dnd_access, Toast.LENGTH_LONG).show()
        refreshDndBanner()
    }

    private fun openDoNotDisturbSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.error_no_dnd_settings, Toast.LENGTH_LONG).show()
        }
    }

    private fun promptSavePreset() {
        val input = EditText(this)
        input.setHint(R.string.preset_name_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.action_save_preset)
            .setView(input)
            .setPositiveButton(R.string.action_save) { _, _ -> savePreset(input.text.toString().trim()) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun savePreset(name: String) {
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.error_preset_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        val snapshot = Preset(
            name = name,
            volumes = StreamType.values().associate { it to controller.volume(it) },
            ringerMode = controller.ringerMode()
        )
        presetStore.save(snapshot)
        Toast.makeText(this, getString(R.string.preset_saved, name), Toast.LENGTH_SHORT).show()
    }

    private class StreamRow(
        val icon: ImageView,
        val label: TextView,
        val value: TextView,
        val seekBar: SeekBar,
        val mute: ImageView
    )
}
