package com.damn.n4splayer.state

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class Settings {
    companion object {
        const val KEY_GPS = "gps"
        const val KEY_SPEED_SCALE = "scale"
        const val KEY_TRACKS_DIR = "tracksDir"

        fun sharedPreferences(context: Context): SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    }
}
