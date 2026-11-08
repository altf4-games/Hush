package com.altf4.hush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Green for "protected" states; the theme itself only carries the blue accent. */
@Composable
fun successColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF1E9E5A) else Color(0xFF4CD08A)

/** Groups a number with thousands separators, e.g. 81,234. */
fun formatCount(n: Long): String = "%,d".format(n)

fun formatCount(n: Int): String = formatCount(n.toLong())

/** A title and a short description on the left, a switch on the right. */
@Composable
fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.padding(top = 12.dp),
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A row of exclusive choices that works like top tabs. */
@Composable
fun SegmentTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(label) },
            )
        }
    }
}

/** A big round on/off control with a power symbol. */
@Composable
fun PowerButton(on: Boolean, onClick: () -> Unit, size: Dp = 132.dp) {
    val ring = if (on) successColor() else MaterialTheme.colorScheme.outline
    val fill = if (on) successColor().copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.42f)) {
            val stroke = Stroke(width = this.size.minDimension * 0.11f, cap = StrokeCap.Round)
            drawArc(
                color = ring,
                startAngle = -55f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(0f, this.size.height * 0.08f),
                size = Size(this.size.width, this.size.height * 0.92f),
                style = stroke,
            )
            drawLine(
                color = ring,
                start = Offset(this.size.width / 2f, 0f),
                end = Offset(this.size.width / 2f, this.size.height * 0.5f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** A small card with a big number and a caption. */
@Composable
fun StatTile(value: String, caption: String, modifier: Modifier = Modifier, accent: Color? = null) {
    HushCard(modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Bars for [values] (oldest first) with a caption under each. The last bar is today and is drawn
 * in the accent colour; the rest are muted.
 */
@Composable
fun BarChart(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 96.dp) {
    val peak = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            val isToday = index == values.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(height).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    // A hairline for empty days so the row still reads as a chart.
                    val barHeight = if (value == 0) 3.dp else (height * (value.toFloat() / peak)).coerceAtLeast(6.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(if (isToday) accent else muted),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = labels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
