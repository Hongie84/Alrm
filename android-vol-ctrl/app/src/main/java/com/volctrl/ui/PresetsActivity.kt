package com.volctrl.ui

import android.app.Activity
import android.app.AlertDialog
import android.media.AudioManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.volctrl.R
import com.volctrl.audio.StreamType
import com.volctrl.audio.VolumeController
import com.volctrl.preset.Preset
import com.volctrl.preset.PresetStore

/** Lists saved presets: tap applies one, long press offers to delete it. */
class PresetsActivity : Activity() {

    private lateinit var controller: VolumeController
    private lateinit var store: PresetStore
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var adapter: PresetAdapter

    private var presets: List<Preset> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presets)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        controller = VolumeController(this)
        store = PresetStore(this)

        listView = findViewById(R.id.listPresets) as ListView
        emptyView = findViewById(R.id.textEmpty) as TextView
        adapter = PresetAdapter()
        listView.adapter = adapter

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            applyPreset(presets[position])
        }
        listView.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { _, _, position, _ ->
                confirmDelete(presets[position])
                true
            }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun reload() {
        presets = store.load()
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (presets.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun applyPreset(preset: Preset) {
        val message = if (controller.apply(preset)) {
            getString(R.string.preset_applied, preset.name)
        } else {
            getString(R.string.error_needs_dnd_access)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(preset: Preset) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_preset_title, preset.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                store.delete(preset.name)
                reload()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** "Media 8 · Ring 5 — Vibrate" */
    private fun describe(preset: Preset): String {
        val volumes = StreamType.values()
            .filter { preset.volumes.containsKey(it) }
            .joinToString(separator = " · ") { stream ->
                getString(stream.labelRes) + " " + preset.volumes[stream]
            }
        val ringer = when (preset.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> getString(R.string.ringer_silent)
            AudioManager.RINGER_MODE_VIBRATE -> getString(R.string.ringer_vibrate)
            else -> getString(R.string.ringer_normal)
        }
        return if (volumes.isEmpty()) ringer else "$volumes — $ringer"
    }

    private inner class PresetAdapter : BaseAdapter() {

        override fun getCount(): Int = presets.size

        override fun getItem(position: Int): Any = presets[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: layoutInflater.inflate(R.layout.item_preset, parent, false)
            val preset = presets[position]
            (view.findViewById(R.id.textPresetName) as TextView).text = preset.name
            (view.findViewById(R.id.textPresetSummary) as TextView).text = describe(preset)
            return view
        }
    }
}
