package com.wanderwildwood.mimidoku.data

import android.content.Context
import androidx.annotation.StringRes
import com.wanderwildwood.mimidoku.R
import com.wanderwildwood.mimidoku.library.Reading
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** How the library is grouped on the first screen. */
enum class Shelving(@StringRes val labelRes: Int) {
    Author(R.string.shelving_author),
    Genre(R.string.shelving_genre),
    Status(R.string.shelving_status),
}

/**
 * How hard the device has to be shaken to call off the sleep timer.
 *
 * The threshold is a multiple of gravity: at rest an accelerometer already reads 1g, so anything
 * at or under that would fire while the device sat on a table. "High" here means high
 * sensitivity — the least shaking needed — which is the way a reader reads the word.
 */
enum class Shake(@StringRes val labelRes: Int, val threshold: Float) {
    Off(R.string.shake_off, Float.MAX_VALUE),
    Low(R.string.shake_low, 2.7f),
    Medium(R.string.shake_medium, 2.2f),
    High(R.string.shake_high, 1.7f),
}

/**
 * What the reader has chosen.
 *
 * Read once at startup and held in memory, because a setting is read on every frame that draws a
 * screen and written about twice a year. Each one writes itself through the moment it changes, so
 * there is no save step to forget.
 */
class Preferences private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("mimidoku", Context.MODE_PRIVATE)

    var shelving: Shelving by choice("shelving", Shelving.Author, Shelving.entries)
    var skipSeconds: Int by number("skipSeconds", 20)
    var autoRewindSeconds: Int by number("autoRewindSeconds", 2)
    var sleepMinutes: Int by number("sleepMinutes", 10)
    var shake: Shake by choice("shake", Shake.High, Shake.entries)

    /**
     * Whether the sleep timer is on, which is a setting rather than something that happened.
     *
     * A reader who wants a timer tonight wanted one last night, so it is remembered: left on, it
     * is still on when the app is opened again, and running out does not turn it off. The clock is
     * what resets.
     */
    var sleepArmed: Boolean by flag("sleepArmed", false)

    /**
     * The nightly window, kept as minutes since midnight.
     *
     * A reader who listens in bed wants the timer every night and never wants to remember to
     * press it, so the app presses it for them: a book started between [autoSleepStart] and
     * [autoSleepEnd] arms the sleep timer itself. Outside those hours nothing happens, because
     * listening at two in the afternoon is not going to bed.
     *
     * Minutes rather than a time: it survives a preferences file as one plain number, it compares
     * with `<` and `>`, and the window is set in half hours anyway. The default is the one most
     * people would have chosen -- ten at night until six in the morning -- but it is off until
     * asked for.
     */
    var autoSleep: Boolean by flag("autoSleep", false)
    var autoSleepStart: Int by number("autoSleepStart", 22 * 60)
    var autoSleepEnd: Int by number("autoSleepEnd", 6 * 60)

    /**
     * How a book should sound. Kept here rather than for the session only: a reader who needs the
     * boost for one quiet recording usually needs it for the next one too, and having to find it
     * again every morning is the sort of thing that makes a setting feel broken.
     */
    var speedTenths: Int by number("speedTenths", 10)
    var volumeBoosted: Boolean by flag("volumeBoosted", false)
    var skipSilence: Boolean by flag("skipSilence", false)

    /**
     * Which reading of chapter lists this library has had.
     *
     * Not a setting — nothing on the settings screen shows it. A file is opened for its chapters
     * once, when its length is read, so when the app learns to read a kind of list it could not
     * read before, the books already known would keep their empty lists. This is what tells it
     * to ask them again, and to ask only once.
     */
    var marksPass: Int by number("marksPass", 0)

    /**
     * An Audiobookshelf server, if the reader has one.
     *
     * The key is an api key made on the server rather than a password, because that is the thing
     * a reader can take back without changing anything else. It is kept in the app's own
     * preferences, sealed with a key that never leaves the phone; see [Secrets].
     */
    var serverUrl: String by text("serverUrl", "")
    var serverToken: String by secret("serverToken")
    var serverLibraryId: String by text("serverLibraryId", "")

    /** Whether there is a server to talk to at all. */
    val hasServer: Boolean get() = serverUrl.isNotBlank() && serverToken.isNotBlank()

    /**
     * What the reader said one granted folder holds, or null where they have not been asked.
     *
     * Kept beside the settings rather than in the library database, because it is an answer about
     * a folder and not something found in one: a rescan rebuilds every book it knows and must not
     * be able to forget this. The grant itself stays the only record that the folder is read at
     * all -- this only says how.
     */
    fun reading(folder: String): Reading? = readings[folder]

    fun setReading(folder: String, reading: Reading) {
        readings = readings + (folder to reading)
        prefs.edit().putString(key(folder), reading.name).apply()
    }

    /** A folder the reader has given back keeps nothing, or it would come back changed. */
    fun forgetReading(folder: String) {
        readings = readings - folder
        prefs.edit().remove(key(folder)).apply()
    }

    private var readings by mutableStateOf(
        prefs.all.keys.filter { it.startsWith(READING) }.mapNotNull { stored ->
            val folder = stored.removePrefix(READING)
            val named = prefs.getString(stored, null)
            Reading.entries.firstOrNull { it.name == named }?.let { folder to it }
        }.toMap(),
    )

    private fun key(folder: String) = READING + folder

    private fun number(key: String, default: Int) = object : ReadWriteProperty<Any?, Int> {
        private var held by mutableStateOf(prefs.getInt(key, default))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = held
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            held = value
            prefs.edit().putInt(key, value).apply()
        }
    }

    private fun text(key: String, default: String) = object : ReadWriteProperty<Any?, String> {
        private var held by mutableStateOf(prefs.getString(key, default) ?: default)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = held
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            held = value
            prefs.edit().putString(key, value).apply()
        }
    }

    /**
     * A setting kept sealed. A key written in the clear by an older version is sealed the first
     * time it is read, rather than waiting for the reader to save it again -- which they may
     * never do. If sealing fails nothing is written at all: an empty key asks again, where a
     * key left in the clear would break the promise the server screen makes.
     */
    private fun secret(key: String) = object : ReadWriteProperty<Any?, String> {
        private var held by mutableStateOf(
            prefs.getString(key, null).let { stored ->
                val plain = Secrets.open(stored).orEmpty()
                if (!stored.isNullOrEmpty() && !Secrets.isSealed(stored)) store(plain)
                plain
            }
        )
        override fun getValue(thisRef: Any?, property: KProperty<*>) = held
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            held = value
            store(value)
        }
        private fun store(value: String) {
            val sealed = Secrets.seal(value)
            if (sealed == null) prefs.edit().remove(key).apply() else prefs.edit().putString(key, sealed).apply()
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

    companion object {
        /** What a per-folder answer is stored under, with the folder's own uri after it. */
        private const val READING = "reading:"

        @Volatile
        private var held: Preferences? = null

        /**
         * One set of settings per process.
         *
         * The screen and the playback service both read these, and each holds its values in
         * memory rather than going back to the file. Two copies would disagree the moment either
         * one wrote -- a sleep timer duration changed on the settings screen would never reach the
         * timer that counts it.
         */
        fun of(context: Context): Preferences =
            held ?: synchronized(this) {
                held ?: Preferences(context.applicationContext).also { held = it }
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
