package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.foundation.IndicationNodeFactory

/**
 * Black on white, and nothing else.
 *
 * The panel has sixteen greys and no colour, so a scheme with tints and containers would only
 * arrive as mud. Everything is one of two values, and anything that needs to stand out does it by
 * shape or weight instead.
 */
private val Monochrome = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    error = Color.Black,
    onError = Color.White,
)

/**
 * Touch feedback is drawn as nothing at all.
 *
 * A ripple is an animation: on e-ink it arrives as a grey smear that then has to be cleared, so
 * the feedback costs two full redraws and looks like a fault. A press that simply does the thing
 * is faster and quieter.
 */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = EmptyNode()
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this

    private class EmptyNode : androidx.compose.ui.Modifier.Node()
}

@Composable
fun MimidokuTheme(content: @Composable () -> Unit) {
    // Material's own components read the typography; a bare Text reads LocalTextStyle, which
    // MaterialTheme leaves alone. Both are set, or half the screen quietly falls back to the
    // system face.
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalTextStyle provides TextStyle(
            fontFamily = Lato,
            fontWeight = Reading,
            color = Color.Black,
        ),
    ) {
        MaterialTheme(colorScheme = Monochrome, typography = MimidokuTypography, content = content)
    }
}
