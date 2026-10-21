package app.playlog.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing
import java.time.Duration
import kotlin.math.roundToInt

/** Right side of the week card: the progress ring, or an invitation to set a goal. */
@Composable
internal fun GoalBadge(goal: WeeklyGoal?, played: Duration, onEdit: () -> Unit) {
    if (goal == null) {
        TextButton(onClick = onEdit) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = PlaylogColors.Lime, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xxs))
            Text(stringResource(R.string.home_goal_set), color = PlaylogColors.Lime)
        }
        return
    }
    val progress = goal.progress(played)
    val label = stringResource(if (goal.isLimit) R.string.home_goal_limit_of else R.string.home_goal_of, goal.hours)
    Column(
        Modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onEdit).padding(Spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GoalRing(progress, goalColor(goal, progress), Modifier.size(RingSize).semantics { contentDescription = label })
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Lime while on track; a limit turns orange near the ceiling and pink past it. */
private fun goalColor(goal: WeeklyGoal, progress: Float): Color = when {
    !goal.isLimit -> if (progress >= 1f) PlaylogColors.Mint else PlaylogColors.Lime
    progress > 1f -> PlaylogColors.Pink
    progress >= LimitWarning -> PlaylogColors.Orange
    else -> PlaylogColors.Lime
}

@Composable
private fun GoalRing(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val sweep by animateFloatAsState(progress.coerceIn(0f, 1f), tween(RingMillis), label = "goal")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(color.copy(alpha = TrackAlpha), 0f, FullCircle, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, StartAngle, FullCircle * sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${(progress * Percent).roundToInt()}%", style = MaterialTheme.typography.titleSmall, color = color)
    }
}

/** "Смотреть историю недели": opens the story recap. */
@Composable
internal fun RecapChip(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.horizontalGradient(listOf(PlaylogColors.Violet, PlaylogColors.Pink)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AutoStories, contentDescription = null, tint = PlaylogColors.Background, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(stringResource(R.string.home_recap), style = MaterialTheme.typography.labelLarge, color = PlaylogColors.Background, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = PlaylogColors.Background, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GoalSheet(current: WeeklyGoal?, onPick: (hours: Int, isLimit: Boolean) -> Unit, onDismiss: () -> Unit) {
    var isLimit by rememberSaveable { mutableStateOf(current?.isLimit ?: false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.home_goal_title), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ModeChip(stringResource(R.string.home_goal_mode_goal), selected = !isLimit) { isLimit = false }
                ModeChip(stringResource(R.string.home_goal_mode_limit), selected = isLimit) { isLimit = true }
            }
            Text(
                stringResource(if (isLimit) R.string.home_goal_limit_hint else R.string.home_goal_goal_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                GoalPresets.forEach { hours ->
                    val selected = current?.hours == hours && current.isLimit == isLimit
                    Box(
                        Modifier
                            .size(PresetSize)
                            .clip(CircleShape)
                            .background(if (selected) PlaylogColors.Lime else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(role = Role.Button) { onPick(hours, isLimit) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.home_goal_hours, hours),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) PlaylogColors.Background else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (current != null) {
                TextButton(onClick = { onPick(0, false) }) { Text(stringResource(R.string.home_goal_remove)) }
            }
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PlaylogColors.Lime,
            selectedLabelColor = PlaylogColors.Background,
        ),
    )
}

private val GoalPresets = listOf(3, 5, 8, 10, 15, 20, 30)
private val RingSize = 64.dp
private val RingStroke = 7.dp
private val PresetSize = 56.dp
private const val RingMillis = 900
private const val TrackAlpha = 0.2f
private const val LimitWarning = 0.8f
private const val FullCircle = 360f
private const val StartAngle = -90f
private const val Percent = 100
