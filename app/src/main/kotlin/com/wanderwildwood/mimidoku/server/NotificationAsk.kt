package com.wanderwildwood.mimidoku.server

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * On Android 13 and later a download's notification, and its "kept" or "could not keep" when
 * the app is closed, show only once notifications are allowed. Asked once, the first time a
 * book is kept; refused, the line along the foot of the shelf still says how it went.
 */
object NotificationAsk {
    private const val PREFS = "notifications"
    private const val ASKED = "asked_on_keep"

    fun shouldAsk(context: Context): Boolean =
        needsAsking(
            sdk = Build.VERSION.SDK_INT,
            granted = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            askedBefore = prefs(context).getBoolean(ASKED, false),
        )

    fun asked(context: Context) {
        prefs(context).edit().putBoolean(ASKED, true).apply()
    }

    /** Only on Android 13 and later, only while not allowed, and only once. */
    fun needsAsking(sdk: Int, granted: Boolean, askedBefore: Boolean): Boolean =
        sdk >= 33 && !granted && !askedBefore

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
