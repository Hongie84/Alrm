package com.volctrl.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.volctrl.preset.Preset

/**
 * Wrapper over [AudioManager] that keeps the SecurityException handling in one place.
 * From API 23 on, silencing the ring or notification stream — and any ringer mode
 * change that leaves or enters silent — throws unless the user has granted Do Not
 * Disturb access, so every mutating call here reports success as a boolean instead.
 */
class VolumeController(context: Context) {

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notifications =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun maxVolume(stream: StreamType): Int = audio.getStreamMaxVolume(stream.streamId)

    fun volume(stream: StreamType): Int = audio.getStreamVolume(stream.streamId)

    /** Returns false when the system rejected the change for want of DND access. */
    fun setVolume(stream: StreamType, value: Int): Boolean {
        val clamped = value.coerceIn(0, maxVolume(stream))
        return try {
            audio.setStreamVolume(stream.streamId, clamped, 0)
            true
        } catch (denied: SecurityException) {
            false
        }
    }

    fun ringerMode(): Int = audio.ringerMode

    fun setRingerMode(mode: Int): Boolean = try {
        audio.ringerMode = mode
        true
    } catch (denied: SecurityException) {
        false
    }

    /** True when the app is allowed to silence the device. */
    fun hasDoNotDisturbAccess(): Boolean = notifications.isNotificationPolicyAccessGranted

    /**
     * Applies a saved preset. The ringer mode goes first: switching out of silent
     * resets the ring stream, which would otherwise undo the volumes set here.
     * Returns false if any single change was rejected.
     */
    fun apply(preset: Preset): Boolean {
        var applied = setRingerMode(preset.ringerMode)
        preset.volumes.forEach { entry ->
            if (!setVolume(entry.key, entry.value)) applied = false
        }
        return applied
    }
}
