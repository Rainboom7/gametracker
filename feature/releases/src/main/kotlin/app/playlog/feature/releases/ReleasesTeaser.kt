package app.playlog.feature.releases

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.data.ReleaseCard
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The next releases as a row of tiles, best match first within the coming weeks; opens the calendar.
 * Renders nothing until the list is in, so a slow or offline store never leaves a hole.
 */
@Composable
fun ReleasesTeaser(onOpenAll: () -> Unit, modifier: Modifier = Modifier, viewModel: ReleasesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val content = state as? ReleasesUiState.Content ?: return
    val soon = content.calendar.days.flatMap { it.games }.take(TeaserCount)
    if (soon.isEmpty()) return
    Column(modifier) {
        Box(Modifier.padding(horizontal = Spacing.lg)) {
            SectionHeader(stringResource(R.string.releases_title)) {
                TextButton(onClick = onOpenAll) { Text(stringResource(R.string.releases_calendar)) }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(soon, key = { it.game.key }) { card -> TeaserTile(card, content.today, onClick = onOpenAll) }
        }
    }
}

@Composable
private fun TeaserTile(card: ReleaseCard, today: LocalDate, onClick: () -> Unit) {
    val game = card.game
    Column(Modifier.width(TileWidth).clickable(role = Role.Button, onClick = onClick), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Box {
            GameImage(
                urls = game.artUrls(),
                contentDescription = null,
                placeholderText = game.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(TileAspect).clip(MaterialTheme.shapes.medium),
            )
            game.date?.let { date ->
                Text(
                    relativeDay(date, today) ?: DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(date),
                    style = MaterialTheme.typography.labelMedium,
                    color = PlaylogColors.Background,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(CircleShape).background(PlaylogColors.Sky)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (card.wishlisted) {
                Text(
                    "★",
                    color = PlaylogColors.Background,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(PlaylogColors.Lime)
                        .padding(horizontal = 6.dp),
                )
            }
        }
        Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.sharedTags.firstOrNull()?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = PlaylogColors.Sky, maxLines = 1)
        }
    }
}

private const val TeaserCount = 10
private val TileWidth = 200.dp
private const val TileAspect = 616f / 353f
