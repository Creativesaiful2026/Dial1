package com.example.core.designsystem

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.animation.core.LinearEasing
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
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
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

/** Remembers where the previous key was pressed so "line motion" can connect two presses. */
private object KeyEffectTrail {
    @Volatile var lastWindow: Offset = Offset.Unspecified
    @Volatile var lastTime: Long = 0L
}

/**
 * Draws one or more animated press effects from the exact point where the key was pressed.
 * Put it BEFORE any clip modifier so effects can spread beyond the key's bounds.
 */
fun Modifier.keyPressEffect(
    effects: List<KeyPressEffect>,
    source: MutableInteractionSource,
    color: Color,
    color2: Color,
    intensity: Float,
    shape: Shape? = null
): Modifier = composed {
    val active = effects.filter { it != KeyPressEffect.NONE }
    if (active.isEmpty()) return@composed this

    val totalDuration = active.maxOf { effectDuration(it) }
    val progress = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var prevOrigin by remember { mutableStateOf(Offset.Unspecified) }
    var seed by remember { mutableStateOf(0) }
    var keyWindow by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(source, active) {
        var running: Job? = null
        // NOTE: collectLatest would cancel the animation as soon as the Release event arrives,
        // freezing the effect on screen. Each press runs in its own coroutine instead.
        source.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                origin = interaction.pressPosition
                val now = System.currentTimeMillis()
                val last = KeyEffectTrail.lastWindow
                prevOrigin = if (last.isSpecified && now - KeyEffectTrail.lastTime < 2500L) {
                    last - keyWindow
                } else {
                    Offset.Unspecified
                }
                KeyEffectTrail.lastWindow = keyWindow + interaction.pressPosition
                KeyEffectTrail.lastTime = now
                seed += 1
                running?.cancel()
                running = launch {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(durationMillis = totalDuration, easing = LinearEasing))
                }
            }
        }
    }

    this
        .onGloballyPositioned { keyWindow = it.positionInWindow() }
        .drawWithContent {
            drawContent()
            val p = progress.value
            if (p < 1f) {
                val k = intensity.coerceIn(0.3f, 1.6f)
                active.forEach { effect ->
                    val t = (p * totalDuration / effectDuration(effect)).coerceIn(0f, 1f)
                    if (t < 1f) {
                        drawKeyEffect(effect, t, origin, prevOrigin, color, color2, k, seed, shape)
                    }
                }
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
    KeyPressEffect.KEY_BORDER -> 600
    KeyPressEffect.LINE_MOTION -> 650
    KeyPressEffect.PULSE_RING -> 800
    KeyPressEffect.SUNRAY -> 700
    KeyPressEffect.UNDERLINE_SWIPE -> 550
    KeyPressEffect.NEON_OUTLINE -> 700
    KeyPressEffect.QUICK_FLASH -> 300
    KeyPressEffect.CONFETTI -> 1100
    KeyPressEffect.SHOCKWAVE -> 800
    KeyPressEffect.ORBIT -> 900
    KeyPressEffect.LOVE_BURST -> 900
    KeyPressEffect.FLOATING_HEARTS -> 1200
    KeyPressEffect.NONE -> 1
}

/** Converts any shape outline into a Path so it can be stroked or filled with drawPath. */
private fun Outline.toPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

private val HEART_PINK = Color(0xFFF43F5E)
private val HEART_SOFT = Color(0xFFFB7185)
private val CONFETTI_COLORS = listOf(
    Color(0xFFF43F5E), Color(0xFFFACC15), Color(0xFF22D3EE),
    Color(0xFF34D399), Color(0xFFA855F7), Color(0xFFFB923C)
)

private fun rnd(seed: Int, i: Int, salt: Int): Float = (((seed * 37 + i * 61 + salt * 17) % 100) + 100) % 100 / 100f

private fun DrawScope.drawHeart(center: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + s * 0.35f)
        cubicTo(center.x - s, center.y - s * 0.2f, center.x - s * 0.5f, center.y - s * 0.9f, center.x, center.y - s * 0.4f)
        cubicTo(center.x + s * 0.5f, center.y - s * 0.9f, center.x + s, center.y - s * 0.2f, center.x, center.y + s * 0.35f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawKeyEffect(
    effect: KeyPressEffect,
    t: Float,
    origin: Offset,
    prevOrigin: Offset,
    c1: Color,
    c2: Color,
    k: Float,
    seed: Int,
    shape: Shape?
) {
    val reach = max(size.width, size.height)
    val fade = (1f - t).coerceIn(0f, 1f)
    val center = Offset(size.width / 2f, size.height / 2f)
    val outline = shape?.createOutline(size, layoutDirection, this)
    fun a(v: Float) = v.coerceIn(0f, 1f)

    when (effect) {
        KeyPressEffect.RIPPLE -> {
            drawCircle(
                color = c1.copy(alpha = a(0.65f * fade * k)),
                radius = reach * (0.2f + 1.3f * t),
                center = origin,
                style = Stroke(width = (3f + 5f * fade) * k)
            )
        }
        KeyPressEffect.GRADIENT_BLOOM -> {
            val radius = reach * (0.35f + 1.4f * t)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c1.copy(alpha = a(0.6f * fade * k)), c2.copy(alpha = a(0.32f * fade * k)), Color.Transparent),
                    center = origin,
                    radius = radius
                ),
                radius = radius,
                center = origin
            )
        }
        KeyPressEffect.WATER_DROP -> {
            for (i in 0 until 3) {
                val tt = ((t * 1.4f) - i * 0.22f).coerceIn(0f, 1f)
                if (tt > 0f && tt < 1f) {
                    drawCircle(
                        color = c1.copy(alpha = a(0.5f * (1f - tt) * k)),
                        radius = reach * (0.1f + 1.1f * tt),
                        center = origin,
                        style = Stroke(width = 2.5f * k + 3f * (1f - tt))
                    )
                }
            }
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45.0) + (seed * 17) % 45)
                val dist = reach * (0.15f + 0.8f * t)
                val pt = Offset(origin.x + (cos(angle) * dist).toFloat(), origin.y + (sin(angle) * dist).toFloat())
                drawCircle(color = c2.copy(alpha = a(0.8f * fade * k)), radius = (5f * (1f - t) + 1f) * k, center = pt)
            }
        }
        KeyPressEffect.WATER_BUBBLES -> {
            for (i in 0 until 7) {
                val phase = ((seed * 31 + i * 53) % 100) / 100f
                val xOff = (phase - 0.5f) * size.width * 0.9f
                val rise = size.height * (0.15f + 0.9f * t) * (0.7f + 0.5f * ((i % 3) / 2f))
                val pt = Offset(origin.x + xOff + sin(t * 6f + i) * 6f, origin.y - rise)
                val r = (4f + (i % 4) * 3f) * k
                drawCircle(color = (if (i % 2 == 0) c1 else c2).copy(alpha = a(0.55f * fade)), radius = r, center = pt, style = Stroke(width = 2f))
                drawCircle(color = Color.White.copy(alpha = a(0.5f * fade)), radius = r * 0.25f, center = Offset(pt.x - r * 0.35f, pt.y - r * 0.35f))
            }
        }
        KeyPressEffect.GLOW_PULSE -> {
            val radius = reach * (0.6f + 0.5f * t)
            drawCircle(
                brush = Brush.radialGradient(colors = listOf(c1.copy(alpha = a(0.55f * fade * k)), Color.Transparent), center = center, radius = radius),
                radius = radius,
                center = center
            )
        }
        KeyPressEffect.SPARKLE -> {
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30.0) + (seed * 23) % 30)
                val dist = reach * (0.1f + 0.95f * t) * (0.7f + 0.3f * ((i % 3) / 2f))
                val pt = Offset(origin.x + (cos(angle) * dist).toFloat(), origin.y + (sin(angle) * dist).toFloat())
                val r = (4.5f * (1f - t) + 1f) * k
                val col = (if (i % 2 == 0) c1 else c2).copy(alpha = a(0.9f * fade))
                drawCircle(color = col, radius = r, center = pt)
                drawLine(col, Offset(pt.x - r * 2f, pt.y), Offset(pt.x + r * 2f, pt.y), strokeWidth = 1.5f)
                drawLine(col, Offset(pt.x, pt.y - r * 2f), Offset(pt.x, pt.y + r * 2f), strokeWidth = 1.5f)
            }
        }
        KeyPressEffect.KEY_BORDER -> {
            val col = c1.copy(alpha = a(0.95f * fade * k))
            if (outline != null) {
                drawPath(outline.toPath(), col, style = Stroke(width = 3.5f * k))
            } else {
                drawRoundRect(col, cornerRadius = CornerRadius(reach * 0.25f), style = Stroke(width = 3.5f * k))
            }
        }
        KeyPressEffect.LINE_MOTION -> {
            if (prevOrigin.isSpecified) {
                val grow = min(1f, t * 2.5f)
                val head = Offset(
                    prevOrigin.x + (origin.x - prevOrigin.x) * grow,
                    prevOrigin.y + (origin.y - prevOrigin.y) * grow
                )
                drawLine(c1.copy(alpha = a(0.9f * fade * k)), prevOrigin, head, strokeWidth = 4f * k, cap = StrokeCap.Round)
                drawCircle(color = c2.copy(alpha = a(0.9f * fade)), radius = 6f * k, center = head)
            } else {
                drawCircle(color = c1.copy(alpha = a(0.5f * fade)), radius = reach * 0.3f * t, center = origin, style = Stroke(width = 3f))
            }
        }
        KeyPressEffect.PULSE_RING -> {
            for (i in 0 until 2) {
                val tt = ((t * 1.3f) - i * 0.3f).coerceIn(0f, 1f)
                if (tt > 0f && tt < 1f) {
                    drawCircle(
                        color = c1.copy(alpha = a(0.45f * (1f - tt) * k)),
                        radius = reach * (0.2f + 0.9f * tt),
                        center = origin,
                        style = Stroke(width = 10f * (1f - tt) + 2f)
                    )
                }
            }
        }
        KeyPressEffect.SUNRAY -> {
            for (i in 0 until 14) {
                val angle = Math.toRadians((i * (360.0 / 14)) + (seed * 7) % 20)
                val r0 = reach * (0.1f + 0.25f * t)
                val r1 = reach * (0.3f + 0.8f * t)
                val s = Offset(origin.x + (cos(angle) * r0).toFloat(), origin.y + (sin(angle) * r0).toFloat())
                val e = Offset(origin.x + (cos(angle) * r1).toFloat(), origin.y + (sin(angle) * r1).toFloat())
                drawLine((if (i % 2 == 0) c1 else c2).copy(alpha = a(0.85f * fade * k)), s, e, strokeWidth = 2.5f * k, cap = StrokeCap.Round)
            }
        }
        KeyPressEffect.UNDERLINE_SWIPE -> {
            val w = size.width
            val xs = ((t * 1.3f) - 0.3f) * w
            val x0 = xs.coerceIn(0f, w)
            val x1 = (xs + w * 0.35f).coerceIn(0f, w)
            if (x1 > x0) {
                val y = size.height - 6f
                drawLine(c1.copy(alpha = a(0.95f * (1f - t * 0.4f) * k)), Offset(x0, y), Offset(x1, y), strokeWidth = 5f * k, cap = StrokeCap.Round)
            }
        }
        KeyPressEffect.NEON_OUTLINE -> {
            val sc = 1f + 0.22f * t
            if (outline != null) {
                withTransform({ scale(sc, sc, center) }) {
                    drawPath(outline.toPath(), c1.copy(alpha = a(0.8f * fade * k)), style = Stroke(width = 6f * fade + 2f))
                    drawPath(outline.toPath(), Color.White.copy(alpha = a(0.6f * fade)), style = Stroke(width = 1.5f))
                }
            } else {
                drawCircle(color = c1.copy(alpha = a(0.8f * fade * k)), radius = reach * 0.5f * sc, center = center, style = Stroke(width = 4f))
            }
        }
        KeyPressEffect.QUICK_FLASH -> {
            val col = Color.White.copy(alpha = a(0.75f * fade * fade * k))
            if (outline != null) drawPath(outline.toPath(), col) else drawRect(col)
        }
        KeyPressEffect.CONFETTI -> {
            for (i in 0 until 22) {
                val dx = (rnd(seed, i, 1) - 0.5f) * reach * 1.6f
                val vy = -(0.3f + 0.5f * rnd(seed, i, 2)) * reach
                val pt = Offset(origin.x + dx * t, origin.y + vy * t + 1.4f * reach * t * t)
                val rot = rnd(seed, i, 3) * 360f + t * 540f
                val w = (5f + 4f * rnd(seed, i, 4)) * k
                val h = w * 0.5f
                val col = CONFETTI_COLORS[i % CONFETTI_COLORS.size].copy(alpha = a(0.95f * fade))
                rotate(rot, pivot = pt) {
                    drawRect(col, topLeft = Offset(pt.x - w / 2f, pt.y - h / 2f), size = Size(w, h))
                }
            }
        }
        KeyPressEffect.SHOCKWAVE -> {
            for (i in 0 until 3) {
                val tt = ((t * 1.5f) - i * 0.18f).coerceIn(0f, 1f)
                if (tt > 0f && tt < 1f) {
                    drawCircle(
                        color = c1.copy(alpha = a(0.8f * (1f - tt) * k)),
                        radius = reach * (0.1f + 1.6f * tt),
                        center = origin,
                        style = Stroke(width = 4f * (1f - tt) + 1f)
                    )
                }
            }
        }
        KeyPressEffect.ORBIT -> {
            val radius = min(size.width, size.height) / 2f * 1.08f + 4f
            val alpha = a(sin(PI.toFloat() * t))
            for (i in 0 until 6) {
                val ang = t * 2f * PI.toFloat() * 1.2f + i * (2f * PI.toFloat() / 6f)
                val pt = Offset(center.x + cos(ang) * radius * (size.width / min(size.width, size.height)), center.y + sin(ang) * radius)
                drawCircle(color = (if (i % 2 == 0) c1 else c2).copy(alpha = a(alpha * 0.95f)), radius = (3.2f + 1.5f * (i % 2)) * k, center = pt)
            }
        }
        KeyPressEffect.LOVE_BURST -> {
            for (i in 0 until 7) {
                val angle = Math.toRadians((i * (360.0 / 7)) + (seed * 11) % 30)
                val dist = reach * (0.2f + 0.9f * t)
                val pt = Offset(origin.x + (cos(angle) * dist).toFloat(), origin.y + (sin(angle) * dist).toFloat())
                val col = (if (i % 3 == 0) HEART_PINK else if (i % 3 == 1) HEART_SOFT else c1).copy(alpha = a(0.95f * fade))
                drawHeart(pt, 10f * (1f - 0.5f * t) * k, col)
            }
        }
        KeyPressEffect.FLOATING_HEARTS -> {
            for (i in 0 until 5) {
                val x = origin.x + (rnd(seed, i, 5) - 0.5f) * size.width * 0.8f + sin(t * 5f + i) * 8f
                val y = origin.y - reach * (0.2f + 1.0f * t) * (0.8f + 0.4f * rnd(seed, i, 6))
                val col = (if (i % 2 == 0) HEART_PINK else HEART_SOFT).copy(alpha = a(0.95f * fade))
                drawHeart(Offset(x, y), (9f + (i % 3) * 3f) * k, col)
            }
        }
        KeyPressEffect.NONE -> Unit
    }
}
