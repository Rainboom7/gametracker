package app.playlog.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.ActivityPeriod

/** Entry to the story recap of the current week, month or year. */
@Composable
internal fun StoryButton(period: ActivityPeriod, onClick: () -> Unit) {
    val title = when (period) {
        ActivityPeriod.WEEK -> R.string.activity_story_WEEK
        ActivityPeriod.MONTH -> R.string.activity_story_MONTH
        ActivityPeriod.YEAR -> R.string.activity_story_YEAR
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.horizontalGradient(listOf(PlaylogColors.Violet, PlaylogColors.Pink, PlaylogColors.Orange)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AutoStories, contentDescription = null, tint = PlaylogColors.Background)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = PlaylogColors.Background)
            Text(stringResource(R.string.activity_story_hint), style = MaterialTheme.typography.bodySmall, color = PlaylogColors.Background)
        }
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PlaylogColors.Background, modifier = Modifier.size(28.dp))
    }
}
