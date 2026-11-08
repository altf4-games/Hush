package com.altf4.hush.ui

import android.icu.text.CompactDecimalFormat
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import java.util.Locale

/** Groups a number with thousands separators, e.g. 81,234. */
fun formatCount(n: Long): String = "%,d".format(n)

fun formatCount(n: Int): String = formatCount(n.toLong())

/** Short form for tiles: exact below 10,000, then 12.4K, 1.2M. */
fun compactCount(n: Long): String =
    if (n < 10_000) formatCount(n)
    else CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT).format(n)

fun compactCount(n: Int): String = compactCount(n.toLong())

/** One crisp confirm/reject tick, as the Android haptics guidance recommends; no long buzzes. */
fun View.toggleHaptic(on: Boolean) {
    val constant = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            if (on) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        else -> HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(constant)
}

// The two shapes the protection control morphs between: a plain circle when paused and a soft
// scalloped "cookie" when protected, after the Material 3 Expressive shape library.
private val PausedShape = RoundedPolygon.circle(numVertices = 10).normalized()
private val ProtectedShape = RoundedPolygon.star(
    numVerticesPerRadius = 10,
    innerRadius = 0.82f,
    rounding = CornerRounding(0.42f),
    innerRounding = CornerRounding(0.42f),
).normalized()
private val OrbMorph = Morph(PausedShape, ProtectedShape)

/**
 * The hero control. Springs from a circle into a slowly turning cookie shape when protection is
 * on, with a soft halo. State is also carried by the icon and by the text beside it, never by
 * colour alone.
 */
@Composable
fun ProtectionOrb(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 176.dp) {
    val status = statusColors
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val progress by animateFloatAsState(if (on) 1f else 0f, HushMotion.expressive(), label = "morph")
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, HushMotion.expressive(), label = "press")
    val fill by animateColorAsState(
        if (on) status.protected else MaterialTheme.colorScheme.surfaceContainerHighest,
        HushMotion.standard(),
        label = "fill",
    )
    val glyph by animateColorAsState(
        if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
        HushMotion.standard(),
        label = "glyph",
    )
    val turn = if (on) {
        val t = rememberInfiniteTransition(label = "turn")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "angle").value
    } else {
        0f
    }
    val halo = status.protected.copy(alpha = 0.22f * progress)
    val path = remember { Path() }

    Box(
        modifier = modifier
            .size(size * 1.3f)
            .semantics {
                contentDescription = "Protection"
                stateDescription = if (on) "On" else "Off"
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Switch) {
                view.toggleHaptic(!on)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (progress > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(halo, Color.Transparent), center = center, radius = this.size.minDimension / 2f),
                    radius = this.size.minDimension / 2f,
                )
            }
        }
        Canvas(Modifier.size(size).scale(press)) {
            path.rewind()
            val shape = OrbMorph.toPath(progress).asComposePath()
            rotate(turn * progress) {
                scale(this.size.width, this.size.height, pivot = Offset.Zero) {
                    drawPath(shape, fill)
                }
            }
        }
        Icon(
            imageVector = if (on) HushIcons.ShieldCheck else HushIcons.Shield,
            contentDescription = null,
            tint = glyph,
            modifier = Modifier.size(size * 0.34f).scale(press),
        )
    }
}

/** A bento tile: small icon badge, a big tabular number and a caption, with optional extra content. */
@Composable
fun StatTile(
    value: String,
    caption: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    extra: (@Composable () -> Unit)? = null,
) {
    HushCard(modifier) {
        IconBadge(icon, tint = tint, container = container, size = 36)
        Spacer(Modifier.height(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (extra != null) {
            Spacer(Modifier.height(12.dp))
            extra()
        }
    }
}

/**
 * Seven stacked bars, oldest first: the blocked part in the accent colour under the allowed part
 * in a muted tone. Values are not printed on the bars; the caption under the chart carries them.
 */
@Composable
fun WeekChart(blocked: List<Int>, total: List<Int>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 112.dp) {
    val peak = (total.maxOrNull() ?: 0).coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        total.forEachIndexed { index, t ->
            val b = blocked.getOrElse(index) { 0 }.coerceAtMost(t)
            val isToday = index == total.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(height).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val barHeight = if (t == 0) 4.dp else (height * (t.toFloat() / peak)).coerceAtLeast(8.dp)
                    val blockedShare = if (t == 0) 0f else b.toFloat() / t
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(muted),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (blockedShare > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(barHeight * blockedShare.coerceAtLeast(0.06f))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isToday) accent else accent.copy(alpha = 0.55f)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = labels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A tiny bar sparkline for tiles: no axes, no labels. */
@Composable
fun Sparkline(values: List<Int>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val peak = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = modifier.fillMaxWidth().height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, v ->
            val share = v.toFloat() / peak
            Box(
                Modifier
                    .weight(1f)
                    .height((28 * share).coerceAtLeast(3f).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (v == 0) track else if (index == values.lastIndex) color else color.copy(alpha = 0.45f)),
            )
        }
    }
}

/** Segmented control for switching between the parts of a screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

/** A step indicator: one short bar per step, filled up to [current]. */
@Composable
fun StepBars(count: Int, current: Int, modifier: Modifier = Modifier) {
    val done = MaterialTheme.colorScheme.primary
    val todo = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val color by animateColorAsState(if (i <= current) done else todo, HushMotion.standard(), label = "step")
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** Padding for the bottom of a scrolling screen so the last card clears the navigation bar. */
val BottomGap = Modifier.padding(bottom = 24.dp)
