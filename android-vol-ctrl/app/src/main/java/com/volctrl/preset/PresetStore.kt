package com.volctrl.preset

import android.content.Context
import android.media.AudioManager
import com.volctrl.audio.StreamType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Presets persisted as a JSON array in SharedPreferences. */
class PresetStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<Preset> {
        val raw = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { readPreset(array.optJSONObject(it)) }
        } catch (malformed: JSONException) {
            emptyList()
        }
    }

    /** Adds the preset, replacing any existing one with the same name. */
    fun save(preset: Preset) {
        val kept = load().filterNot { it.name.equals(preset.name, ignoreCase = true) }
        write(kept + preset)
    }

    fun delete(name: String) {
        write(load().filterNot { it.name == name })
    }

    private fun write(presets: List<Preset>) {
        val array = JSONArray()
        presets.forEach { array.put(writePreset(it)) }
        prefs.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    private fun writePreset(preset: Preset): JSONObject {
        val volumes = JSONObject()
        preset.volumes.forEach { entry -> volumes.put(entry.key.key, entry.value) }
        return JSONObject()
            .put(FIELD_NAME, preset.name)
            .put(FIELD_VOLUMES, volumes)
            .put(FIELD_RINGER_MODE, preset.ringerMode)
    }

    private fun readPreset(json: JSONObject?): Preset? {
        if (json == null) return null
        val name = json.optString(FIELD_NAME)
        if (name.isNullOrEmpty()) return null
        val volumesJson = json.optJSONObject(FIELD_VOLUMES) ?: JSONObject()
        val volumes = LinkedHashMap<StreamType, Int>()
        StreamType.values().forEach { stream ->
            if (volumesJson.has(stream.key)) volumes[stream] = volumesJson.optInt(stream.key)
        }
        return Preset(name, volumes, json.optInt(FIELD_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL))
    }

    private companion object {
        const val PREFS_NAME = "volctrl_presets"
        const val KEY_PRESETS = "presets"
        const val FIELD_NAME = "name"
        const val FIELD_VOLUMES = "volumes"
        const val FIELD_RINGER_MODE = "ringerMode"
    }
}
