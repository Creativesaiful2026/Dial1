package com.example.feature.customization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.datastore.DialMeConfig
import com.example.core.designsystem.dialKeyShape
import com.example.core.designsystem.keyPressEffect

private val PREVIEW_KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
private val PREVIEW_LETTERS = mapOf(
    "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
    "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+"
)

/**
 * A live, tappable miniature of the dialpad that reflects the current customization
 * (shape, glass, border, spacing, letters, press effect). Pinned above the settings list.
 */
@Composable
fun DialpadPreviewPanel(config: DialMeConfig, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val spacing = config.dialpadSpacingDp.coerceIn(3, 10).dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(cs.surfaceContainerHigh, cs.background)))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        PREVIEW_KEYS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                row.forEach { key ->
                    PreviewKey(key = key, letters = PREVIEW_LETTERS[key], config = config, modifier = Modifier.weight(1f))
                }
            }
        }
        Text(
            text = stringResource(R.string.dialpad_preview_hint),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PreviewKey(key: String, letters: String?, config: DialMeConfig, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = dialKeyShape(config.dialpadButtonShape, config.dialpadCornerRadiusDp)
    val source = remember { MutableInteractionSource() }
    val isDark = cs.background.luminance() < 0.5f
    val tint = if (isDark) Color.White else cs.primary
    val glassAlpha = config.dialpadGlassAlpha.coerceIn(0.05f, 0.6f)
    val glassBrush = Brush.linearGradient(
        listOf(
            tint.copy(alpha = (glassAlpha * 1.6f).coerceAtMost(0.9f)),
            tint.copy(alpha = glassAlpha * 0.45f)
        )
    )
    val glassBorder = BorderStroke(
        1.dp,
        Brush.linearGradient(
            listOf(tint.copy(alpha = if (isDark) 0.55f else 0.45f), tint.copy(alpha = 0.08f))
        )
    )
    Surface(
        modifier = modifier
            .height(38.dp)
            .keyPressEffect(
                effect = config.keyPressEffect,
                source = source,
                color = cs.primary,
                color2 = cs.secondary,
                intensity = config.keyEffectIntensity
            )
            .clip(shape)
            .clickable(interactionSource = source, indication = null, onClick = {}),
        shape = shape,
        color = if (config.dialpadGlass) Color.Transparent
        else cs.surfaceContainer.copy(alpha = config.cardTransparencyAlpha),
        border = if (config.dialpadGlass && config.dialpadKeyBorder) glassBorder else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (config.dialpadGlass) Modifier.background(glassBrush) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                if (config.showDialpadLetters && letters != null) {
                    Text(
                        text = " $letters",
                        fontSize = 8.sp,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}
