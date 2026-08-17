package com.volctrl.preset

import com.volctrl.audio.StreamType

/** A named snapshot of every stream volume plus the ringer mode. */
data class Preset(
    val name: String,
    val volumes: Map<StreamType, Int>,
    val ringerMode: Int
)
