package com.wanderwildwood.mimidoku.ui

import androidx.compose.runtime.Composable
import com.mudita.mmd.ThemeMMD

/**
 * Black on white, from MMD.
 *
 * This used to be a monochrome colour scheme, an object to suppress the ripple, and a
 * typography wrapping a bundled copy of Lato. All three were right, and all three are what
 * ThemeMMD already does — so what stood here was a careful reimplementation of a library
 * this app already depended on and used for exactly one component.
 *
 * The font is the same font: MMD bundles Lato too, for the same reason this app did. The
 * four ttf files this app carried have gone with the rest of it — 876 KB of them — and the
 * type now comes from the same place as every other app's.
 */
@Composable
fun MimidokuTheme(content: @Composable () -> Unit) = ThemeMMD(colorScheme = monochrome, content = content)
