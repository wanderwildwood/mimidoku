package com.wanderwildwood.mimidoku.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.mimidoku.R

/**
 * From Android 13 on, an app installed from a file rather than a store is kept out of some
 * settings: Accessibility, notification access and, from Android 15, the default phone and
 * texting roles. They open only once App info, then ⋮, then Allow restricted settings has been
 * pressed. Android 12 has none of this, so there [blocked] is always false and nothing shows.
 */
object RestrictedSettings {

    /**
     * Whether this app is one Android keeps out: installed from a file, on Android [since] or
     * later (13 for Accessibility and notification access, 15 for the default roles). Whether
     * the person has already let it in cannot be read by the app itself, so this is asked only
     * once a request has come back refused, and the hint says to try again.
     */
    fun blocked(context: Context, since: Int = 33): Boolean =
        Build.VERSION.SDK_INT >= since && Build.VERSION.SDK_INT >= 33 && fromFile(context)

    private fun fromFile(context: Context): Boolean = runCatching {
        val source = context.packageManager.getInstallSourceInfo(context.packageName).packageSource
        source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE || source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
    }.getOrDefault(false)

    fun appInfo(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:" + context.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** The two lines saying how to let the app in, and the button to App info. */
@Composable
fun RestrictedHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        TextMMD(text = stringResource(R.string.restricted_hint), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(6.dp))
        OutlinedButtonMMD(
            onClick = { runCatching { context.startActivity(RestrictedSettings.appInfo(context)) } },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.restricted_app_info), style = MaterialTheme.typography.bodySmall) }
    }
}
