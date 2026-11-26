package com.example.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneEnabled
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.datastore.ThemeMode
import java.io.File
import kotlin.math.abs

@Composable
fun DialMeBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val config = LocalDialMeConfig.current
    val cs = MaterialTheme.colorScheme
    val bgFile = config.customBackgroundPath?.let { File(it) }?.takeIf { it.exists() }

    Box(modifier = modifier.fillMaxSize().background(cs.background)) {
        if (bgFile != null) {
            AsyncImage(
                model = bgFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.32f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cs.background.copy(alpha = 0.62f))
            )
        } else if (config.useGradientBackground && config.themeMode != ThemeMode.AMOLED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                cs.primary.copy(alpha = 0.14f),
                                cs.background,
                                cs.secondary.copy(alpha = 0.10f)
                            )
                        )
                    )
            )
        }
        content()
    }
}

@Composable
fun DialMeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    val config = LocalDialMeConfig.current
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(config.dialpadCornerRadiusDp.coerceIn(12, 28).dp)
    val effectiveColor = containerColor.copy(alpha = config.cardTransparencyAlpha.coerceIn(0.45f, 1f))
    val border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.28f))

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = effectiveColor,
            contentColor = cs.onSurface,
            border = border
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = effectiveColor,
            contentColor = cs.onSurface,
            border = border
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun ContactAvatar(
    name: String,
    photoUri: String?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val palette = listOf(
        Color(0xFF14B8A6),
        Color(0xFF3B82F6),
        Color(0xFFF97316),
        Color(0xFF8B5CF6),
        Color(0xFFEC4899),
        Color(0xFF10B981)
    )
    val bg = palette[abs(name.hashCode()) % palette.size]
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "#" }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrBlank()) {
            AsyncImage(
                model = photoUri,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(
                text = initials,
                color = bg,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun RoleDialerBanner(
    onSetDefaultClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    DialMeCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = cs.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhoneEnabled,
                    contentDescription = null,
                    tint = cs.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.role_dialer_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.role_dialer_banner_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSetDefaultClick,
                modifier = Modifier.testTag("set_default_dialer_button"),
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
            ) {
                Text(stringResource(R.string.role_dialer_cta))
            }
        }
    }
}

@Composable
fun PermissionCalloutCard(
    title: String,
    description: String,
    onGrantClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    DialMeCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = cs.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSettingsClick) {
                    Text(stringResource(R.string.permissions_open_settings))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier.testTag("grant_permission_button")
                ) {
                    Text(stringResource(R.string.permissions_grant))
                }
            }
        }
    }
}
