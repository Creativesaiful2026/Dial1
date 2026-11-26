package com.example.feature.statistics

import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.DateTimeUtils
import com.example.core.designsystem.DialMeCard
import com.example.core.designsystem.JetBrainsMonoFontFamily
import com.example.feature.common.DialMeMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: DialMeMainViewModel,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val stats by viewModel.callStatistics.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.ui_call_statistics)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatKpiCard("Total Calls", stats.totalCalls.toString(), Modifier.weight(1f))
                    StatKpiCard("Talk Time", DateTimeUtils.formatDurationReadable(stats.totalTalkSeconds), Modifier.weight(1f))
                    StatKpiCard("Avg Call", DateTimeUtils.formatDurationReadable(stats.averageTalkSeconds), Modifier.weight(1f))
                }
            }

            // Donut Breakdown Card
            item {
                DialMeCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ui_call_direction_breakdown), style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val slices = listOf(
                                Triple("Incoming", stats.incomingCount, Color(0xFF10B981)),
                                Triple("Outgoing", stats.outgoingCount, cs.primary),
                                Triple("Missed", stats.missedCount, cs.error),
                                Triple("Rejected", stats.rejectedCount, Color(0xFFF59E0B))
                            )
                            val total = slices.sumOf { it.second }.coerceAtLeast(1)

                            Canvas(modifier = Modifier.size(120.dp).padding(8.dp)) {
                                var startAngle = -90f
                                slices.forEach { (_, count, color) ->
                                    val sweep = (count.toFloat() / total.toFloat()) * 360f
                                    if (sweep > 0f) {
                                        drawArc(
                                            color = color,
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            style = Stroke(width = 22f, cap = StrokeCap.Butt)
                                        )
                                        startAngle += sweep
                                    }
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                slices.forEach { (label, count, color) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color) }
                                        Spacer(Modifier.width(8.dp))
                                        Text("$label: $count", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 24-Hour Activity Bar Chart
            item {
                DialMeCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.ui_hourly_call_activity_0h_23h), style = MaterialTheme.typography.titleMedium)
                        val maxHour = (stats.hourlyCounts.maxOrNull() ?: 1).coerceAtLeast(1)
                        val barColor = cs.primary
                        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                            val barWidth = size.width / 24f
                            stats.hourlyCounts.forEachIndexed { idx, count ->
                                val barHeight = (count.toFloat() / maxHour.toFloat()) * (size.height - 12f)
                                drawRect(
                                    color = barColor.copy(alpha = if (count > 0) 0.9f else 0.15f),
                                    topLeft = Offset(idx * barWidth + 2f, size.height - barHeight.coerceAtLeast(4f)),
                                    size = Size((barWidth - 4f).coerceAtLeast(2f), barHeight.coerceAtLeast(4f))
                                )
                            }
                        }
                    }
                }
            }

            // Top Contacts
            if (stats.topContacts.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.ui_top_contacts), style = MaterialTheme.typography.titleMedium)
                }
                items(stats.topContacts, key = { it.phoneNumber }) { c ->
                    DialMeCard {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(c.phoneNumber, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.ui_calls, c.callCount), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                                Text(DateTimeUtils.formatDurationReadable(c.totalDurationSeconds), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatKpiCard(title: String, value: String, modifier: Modifier = Modifier) {
    DialMeCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
