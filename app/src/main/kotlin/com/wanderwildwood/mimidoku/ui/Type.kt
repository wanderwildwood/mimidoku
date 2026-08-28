package com.wanderwildwood.mimidoku.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.wanderwildwood.mimidoku.R

/**
 * Lato, bundled rather than asked for.
 *
 * The device's own default is a grotesque with tight apertures, which on a low-contrast panel at
 * arm's length closes up: c and e start to read as o. Lato's are open, and its stroke holds at the
 * sizes this app uses. It ships with the app because a font that has to be fetched is a font that
 * is missing on a device with no network, which is most of the time here.
 *
 * SIL Open Font License 1.1 — see LICENSES/OFL-1.1.txt.
 */
val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_semibold, FontWeight.SemiBold),
    Font(R.font.lato_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.lato_bold, FontWeight.Bold),
)

/**
 * Everything the app sets in type is semibold.
 *
 * Regular Lato is drawn for paper. The panel loses the thin end of every stroke, so regular reads
 * as grey and semibold reads as black; there is no bold to escalate to afterwards because nothing
 * here needs to shout.
 */
val Reading = FontWeight.SemiBold

/** Material's scale, in Lato. Sizes are set at each call site, not taken from here. */
val MimidokuTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Lato),
        displayMedium = displayMedium.copy(fontFamily = Lato),
        displaySmall = displaySmall.copy(fontFamily = Lato),
        headlineLarge = headlineLarge.copy(fontFamily = Lato),
        headlineMedium = headlineMedium.copy(fontFamily = Lato),
        headlineSmall = headlineSmall.copy(fontFamily = Lato),
        titleLarge = titleLarge.copy(fontFamily = Lato),
        titleMedium = titleMedium.copy(fontFamily = Lato),
        titleSmall = titleSmall.copy(fontFamily = Lato),
        bodyLarge = bodyLarge.copy(fontFamily = Lato),
        bodyMedium = bodyMedium.copy(fontFamily = Lato),
        bodySmall = bodySmall.copy(fontFamily = Lato),
        labelLarge = labelLarge.copy(fontFamily = Lato),
        labelMedium = labelMedium.copy(fontFamily = Lato),
        labelSmall = labelSmall.copy(fontFamily = Lato),
    )
}
