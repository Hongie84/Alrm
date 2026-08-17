package com.volctrl.audio

import android.media.AudioManager
import com.volctrl.R

/** The audio streams the app exposes, in the order they are shown. */
enum class StreamType(
    val streamId: Int,
    val key: String,
    val labelRes: Int,
    val iconRes: Int
) {
    MEDIA(AudioManager.STREAM_MUSIC, "media", R.string.stream_media, R.drawable.ic_media),
    RING(AudioManager.STREAM_RING, "ring", R.string.stream_ring, R.drawable.ic_ring),
    NOTIFICATION(
        AudioManager.STREAM_NOTIFICATION,
        "notification",
        R.string.stream_notification,
        R.drawable.ic_notification
    ),
    ALARM(AudioManager.STREAM_ALARM, "alarm", R.string.stream_alarm, R.drawable.ic_alarm),
    SYSTEM(AudioManager.STREAM_SYSTEM, "system", R.string.stream_system, R.drawable.ic_system),
    CALL(AudioManager.STREAM_VOICE_CALL, "call", R.string.stream_call, R.drawable.ic_call);

    companion object {
        fun fromKey(key: String): StreamType? = values().firstOrNull { it.key == key }
    }
}
