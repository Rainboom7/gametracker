package app.playlog.feature.journal

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.playlog.core.model.Badge

/**
 * A round medal: the icon in a disc, an arc for progress to the next level and one pip per level.
 * Locked badges are greyed out; a recent unlock breathes with a soft accent halo.
 */
@Composable
fun BadgeMedal(badge: Badge, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val earned = badge.level > 0
    val accent = if (earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (badge.isRecent) Halo(accent)
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * RingWidth
            val inset = stroke / 2 + this.size.minDimension * RingInset
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(track, 0f, FullCircle, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(
                accent,
                StartAngle,
                FullCircle * badge.milestone.progressToNext,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Box(
            Modifier.size(size * DiscScale).clip(CircleShape).background(accent.copy(alpha = if (earned) DiscEarned else DiscLocked)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(badgeIcon(badge.kind), contentDescription = null, tint = accent, modifier = Modifier.size(size * IconScale))
        }
        LevelPips(badge, accent, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Halo(accent: Color) {
    val alpha by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = HaloMin,
        targetValue = HaloMax,
        animationSpec = infiniteRepeatable(tween(HaloMillis), RepeatMode.Reverse),
        label = "halo",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = alpha), Color.Transparent)), radius = size.minDimension / 2)
    }
}

@Composable
private fun LevelPips(badge: Badge, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(badge.milestone.maxLevel) { i ->
            Box(Modifier.size(4.dp).clip(CircleShape).background(if (i < badge.level) accent else MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

private const val FullCircle = 360f
private const val StartAngle = -90f
private const val RingWidth = 0.06f
private const val RingInset = 0.04f
private const val DiscScale = 0.68f
private const val IconScale = 0.34f
private const val DiscEarned = 0.18f
private const val DiscLocked = 0.08f
private const val HaloMin = 0.12f
private const val HaloMax = 0.35f
private const val HaloMillis = 1_400
