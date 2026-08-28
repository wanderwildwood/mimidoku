package com.wanderwildwood.mimidoku.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** How the library is grouped on the first screen. */
enum class Shelving(val label: String) {
    Author("Author"),
    Genre("Genre"),
    Status("Status"),
}

/**
 * How hard the device has to be shaken to call off the sleep timer.
 *
 * The threshold is a multiple of gravity: at rest an accelerometer already reads 1g, so anything
 * at or under that would fire while the device sat on a table. "High" here means high
 * sensitivity — the least shaking needed — which is the way a reader reads the word.
 */
enum class Shake(val label: String, val threshold: Float) {
    Off("Off", Float.MAX_VALUE),
    Low("Low", 2.7f),
    Medium("Medium", 2.2f),
    High("High", 1.7f),
}

/**
 * What the reader has chosen.
 *
 * Read once at startup and held in memory, because a setting is read on every frame that draws a
 * screen and written about twice a year. Each one writes itself through the moment it changes, so
 * there is no save step to forget.
 */
class Preferences(context: Context) {

    private val prefs = context.getSharedPreferences("mimidoku", Context.MODE_PRIVATE)

    var shelving: Shelving by choice("shelving", Shelving.Author, Shelving.entries)
    var skipSeconds: Int by number("skipSeconds", 20)
    var autoRewindSeconds: Int by number("autoRewindSeconds", 2)
    var sleepMinutes: Int by number("sleepMinutes", 10)
    var shake: Shake by choice("shake", Shake.High, Shake.entries)

    /**
     * How a book should sound. Kept here rather than for the session only: a reader who needs the
     * boost for one quiet recording usually needs it for the next one too, and having to find it
     * again every morning is the sort of thing that makes a setting feel broken.
     */
    var speedTenths: Int by number("speedTenths", 10)
    var volumeBoosted: Boolean by flag("volumeBoosted", false)
    var skipSilence: Boolean by flag("skipSilence", false)

    private fun number(key: String, default: Int) = object : ReadWriteProperty<Any?, Int> {
        private var held by mutableStateOf(prefs.getInt(key, default))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = held
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            held = value
            prefs.edit().putInt(key, value).apply()
        }
    }

    private fun flag(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        private var held by mutableStateOf(prefs.getBoolean(key, default))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = held
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            held = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    private fun <T : Enum<T>> choice(key: String, default: T, all: List<T>) =
        object : ReadWriteProperty<Any?, T> {
            // Stored by name rather than ordinal: an ordinal silently means something else the
            // first time an option is inserted in the middle of the list.
            private var held by mutableStateOf(
                prefs.getString(key, null)?.let { saved -> all.firstOrNull { it.name == saved } }
                    ?: default,
            )
            override fun getValue(thisRef: Any?, property: KProperty<*>) = held
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                held = value
                prefs.edit().putString(key, value.name).apply()
            }
        }
}
