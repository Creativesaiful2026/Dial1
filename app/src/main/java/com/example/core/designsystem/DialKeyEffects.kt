package com.example.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.core.datastore.DialpadButtonShape
import com.example.core.datastore.KeyPressEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Maps the chosen keypad button shape to a Compose [Shape]. */
fun dialKeyShape(shape: DialpadButtonShape, cornerDp: Int): Shape = when (shape) {
    DialpadButtonShape.CIRCLE -> CircleShape
    DialpadButtonShape.SQUIRCLE -> RoundedCornerShape(cornerDp.dp)
    DialpadButtonShape.ROUNDED_RECT -> RoundedCornerShape(12.dp)
    DialpadButtonShape.PILL -> RoundedCornerShape(50)
    DialpadButtonShape.SOFT_SQUARE -> RoundedCornerShape(30)
    DialpadButtonShape.LEAF -> RoundedCornerShape(
        topStartPercent = 50, topEndPercent = 10, bottomEndPercent = 50, bottomStartPercent = 10
    )
    DialpadButtonShape.DROP -> RoundedCornerShape(
        topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 8, bottomStartPercent = 50
    )
    DialpadButtonShape.CUT_CORNER -> CutCornerShape(20)
    DialpadButtonShape.HEXAGON -> polygonShape(6, 0f)
    DialpadButtonShape.DIAMOND -> polygonShape(4, 0f)
    DialpadButtonShape.OCTAGON -> polygonShape(8, 22.5f)
}

private fun polygonShape(sides: Int, rotationDeg: Float): Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    for (i in 0 until sides) {
        val angle = Math.toRadians((360.0 / sides) * i + rotationDeg - 90.0)
        val x = (cx + cx * cos(angle)).toFloat()
        val y = (cy + cy * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * Draws an animated effect (gradient bloom, water ripples, bubbles, sparkles...) from the
 * exact point where the key was pressed. Put it BEFORE any clip modifier so the effect can
 * spread beyond the key's bounds.
 */
fun Modifier.keyPressEffect(
    effect: KeyPressEffect,
    source: MutableInteractionSource,
    color: Color,
    color2: Color,
    intensity: Float
): Modifier = composed {
    if (effect == KeyPressEffect.NONE) return@composed this

    val progress = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var seed by remember { mutableStateOf(0) }

    LaunchedEffect(source, effect) {
        var running: Job? = null
        // NOTE: collectLatest would cancel the animation as soon as the Release event arrives,
        // freezing the effect on screen. Each press runs in its own coroutine instead.
        source.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                origin = interaction.pressPosition
                seed += 1
                running?.cancel()
                running = launch {
                    progress.snapTo(0f)
                    progress.animateTo(
                        1f,
                        tween(durationMillis = effectDuration(effect), easing = LinearOutSlowInEasing)
                    )
                }
            }
        }
    }

    this.drawWithContent {
        drawContent()
        val t = progress.value
        if (t < 1f) {
            drawKeyEffect(effect, t, origin, color, color2, intensity.coerceIn(0.3f, 1.6f), seed)
        }
    }
}

private fun effectDuration(effect: KeyPressEffect): Int = when (effect) {
    KeyPressEffect.RIPPLE -> 650
    KeyPressEffect.GRADIENT_BLOOM -> 700
    KeyPressEffect.WATER_DROP -> 900
    KeyPressEffect.WATER_BUBBLES -> 1000
    KeyPressEffect.GLOW_PULSE -> 550
    KeyPressEffect.SPARKLE -> 800
    KeyPressEffect.NONE -> 1
}

private fun DrawScope.drawKeyEffect(
    effect: KeyPressEffect,
    t: Float,
    origin: Offset,
    c1: Color,
    c2: Color,
    k: Float,
    seed: Int
) {
    val reach = max(size.width, size.height)
    val fade = (1f - t).coerceIn(0f, 1f)
    when (effect) {
        KeyPressEffect.RIPPLE -> {
            drawCircle(
                color = c1.copy(alpha = (0.65f * fade * k).coerceIn(0f, 1f)),
                radius = reach * (0.2f + 1.3f * t),
                center = origin,
                style = Stroke(width = (3f + 5f * fade) * k)
            )
        }
        KeyPressEffect.GRADIENT_BLOOM -> {
            val radius = reach * (0.35f + 1.4f * t)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        c1.copy(alpha = (0.6f * fade * k).coerceIn(0f, 1f)),
                        c2.copy(alpha = (0.32f * fade * k).coerceIn(0f, 1f)),
                        Color.Transparent
                    ),
                    center = origin,
                    radius = radius
                ),
                radius = radius,
                center = origin
            )
        }
        KeyPressEffect.WATER_DROP -> {
            // Three staggered rings, like a drop hitting water...
            for (i in 0 until 3) {
                val tt = ((t * 1.4f) - i * 0.22f).coerceIn(0f, 1f)
                if (tt > 0f && tt < 1f) {
                    drawCircle(
                        color = c1.copy(alpha = (0.5f * (1f - tt) * k).coerceIn(0f, 1f)),
                        radius = reach * (0.1f + 1.1f * tt),
                        center = origin,
                        style = Stroke(width = 2.5f * k + 3f * (1f - tt))
                    )
                }
            }
            // ...and little droplets flying outwards.
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45.0) + (seed * 17) % 45)
                val dist = reach * (0.15f + 0.8f * t)
                val p = Offset(
                    origin.x + (cos(angle) * dist).toFloat(),
                    origin.y + (sin(angle) * dist).toFloat()
                )
                drawCircle(
                    color = c2.copy(alpha = (0.8f * fade * k).coerceIn(0f, 1f)),
                    radius = (5f * (1f - t) + 1f) * k,
                    center = p
                )
            }
        }
        KeyPressEffect.WATER_BUBBLES -> {
            for (i in 0 until 7) {
                val phase = ((seed * 31 + i * 53) % 100) / 100f
                val xOff = (phase - 0.5f) * size.width * 0.9f
                val rise = size.height * (0.15f + 0.9f * t) * (0.7f + 0.5f * ((i % 3) / 2f))
                val p = Offset(origin.x + xOff + sin(t * 6f + i).toFloat() * 6f, origin.y - rise)
                val r = (4f + (i % 4) * 3f) * k
                drawCircle(
                    color = (if (i % 2 == 0) c1 else c2).copy(alpha = (0.55f * fade).coerceIn(0f, 1f)),
                    radius = r,
                    center = p,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = (0.5f * fade).coerceIn(0f, 1f)),
                    radius = r * 0.25f,
                    center = Offset(p.x - r * 0.35f, p.y - r * 0.35f)
                )
            }
        }
        KeyPressEffect.GLOW_PULSE -> {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = reach * (0.6f + 0.5f * t)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        c1.copy(alpha = (0.55f * fade * k).coerceIn(0f, 1f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        KeyPressEffect.SPARKLE -> {
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30.0) + (seed * 23) % 30)
                val dist = reach * (0.1f + 0.95f * t) * (0.7f + 0.3f * ((i % 3) / 2f))
                val p = Offset(
                    origin.x + (cos(angle) * dist).toFloat(),
                    origin.y + (sin(angle) * dist).toFloat()
                )
                val r = (4.5f * (1f - t) + 1f) * k
                val col = (if (i % 2 == 0) c1 else c2).copy(alpha = (0.9f * fade).coerceIn(0f, 1f))
                drawCircle(color = col, radius = r, center = p)
                drawLine(col, Offset(p.x - r * 2f, p.y), Offset(p.x + r * 2f, p.y), strokeWidth = 1.5f)
                drawLine(col, Offset(p.x, p.y - r * 2f), Offset(p.x, p.y + r * 2f), strokeWidth = 1.5f)
            }
        }
        KeyPressEffect.NONE -> Unit
    }
}
