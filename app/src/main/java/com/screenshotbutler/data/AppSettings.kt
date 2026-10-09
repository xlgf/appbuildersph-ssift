package com.screenshotbutler.data

import android.content.Context
import com.screenshotbutler.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small on-device preferences. Demo mode is on by default in debug builds, the ones used on stage. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("butler", Context.MODE_PRIVATE)

    private val _demoMode = MutableStateFlow(prefs.getBoolean(KEY_DEMO, BuildConfig.DEBUG))
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    /**
     * Outside demo mode, index only the newest N screenshots (0 = all). No UI: a test knob for trying real
     * screenshots without the ~25 min first index of a full phone. Set it in shared_prefs/butler.xml.
     */
    val realLimit: Int = prefs.getInt(KEY_REAL_LIMIT, 0)

    /** The [com.screenshotbutler.index.Tagger] rules version the stored tags were made with. */
    var tagVersion: Int
        get() = prefs.getInt(KEY_TAG_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_TAG_VERSION, value).apply()

    fun setDemoMode(on: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO, on).apply()
        _demoMode.value = on
    }

    companion object {
        private const val KEY_DEMO = "demo_mode"
        private const val KEY_REAL_LIMIT = "real_limit"
        private const val KEY_TAG_VERSION = "tag_version"

        /** Demo screenshots live in Pictures/Screenshots/ButlerDemo (see tools/push-demo-screenshots.ps1). */
        const val DEMO_FOLDER = "ButlerDemo"
    }
}
