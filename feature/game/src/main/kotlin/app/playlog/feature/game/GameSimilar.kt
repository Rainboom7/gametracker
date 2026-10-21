package app.playlog.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.component.GradientCard
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing

/** Genres as tags plus a jump into "Спроси" with "как <title>": the store games most like this one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SimilarCard(title: String, genres: List<String>, onFindSimilar: () -> Unit) {
    val accents = listOf(PlaylogColors.Violet, PlaylogColors.Sky, PlaylogColors.Pink, PlaylogColors.Orange)
    GradientCard(accents = listOf(PlaylogColors.Violet, PlaylogColors.Pink), onClick = onFindSimilar, modifier = Modifier.fillMaxWidth()) {
        if (genres.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                genres.forEachIndexed { i, genre ->
                    val accent = accents[i % accents.size]
                    Text(
                        genre,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(accent.copy(alpha = TagAlpha))
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PlaylogColors.Violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(Spacing.xs))
            Text(stringResource(R.string.game_similar, title), style = MaterialTheme.typography.titleMedium, color = PlaylogColors.Violet)
        }
        Text(stringResource(R.string.game_similar_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val TagAlpha = 0.16f
