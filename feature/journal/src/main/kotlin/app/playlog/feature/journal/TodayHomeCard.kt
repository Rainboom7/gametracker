package app.playlog.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.GameId
import app.playlog.core.model.TodayItem
import app.playlog.core.ui.formatPlaytime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Сегодня": what you played or captured on this day in past years, first-play anniversaries, and
 * wishlist sales or giveaways that end within a day or two. Renders nothing on an ordinary day.
 */
@Composable
fun TodayHomeCard(
    onOpenGame: (GameId) -> Unit,
    onOpenScreenshots: (GameId?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    if (items.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    val open: (TodayItem) -> Unit = { item ->
        when (item) {
            is TodayItem.PlayedOnThisDay -> onOpenGame(item.entry.primary.id)
            is TodayItem.FirstPlayedAnniversary -> onOpenGame(item.entry.primary.id)
            is TodayItem.ShotsOnThisDay -> onOpenScreenshots(item.entry?.primary?.id)
            // No browser installed throws on Android; a dead tap beats a crash.
            is TodayItem.SaleEndingSoon -> runCatching { uriHandler.openUri(item.item.storeUrl) }
            is TodayItem.GiveawayEndingSoon -> runCatching { uriHandler.openUri(item.giveaway.url) }
        }
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items.forEach { item -> TodayRow(item, onClick = { open(item) }) }
        }
    }
}

@Composable
private fun TodayRow(item: TodayItem, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RowAlpha)) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            GameImage(
                urls = images(item),
                contentDescription = null,
                modifier = Modifier.size(ThumbSize).clip(MaterialTheme.shapes.small),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Icon(icon(item), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(kicker(item), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(headline(item), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    detail(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun images(item: TodayItem): List<String?> = when (item) {
    is TodayItem.PlayedOnThisDay -> listOf(item.entry.coverUrl, item.entry.heroUrl)
    is TodayItem.FirstPlayedAnniversary -> listOf(item.entry.coverUrl, item.entry.heroUrl)
    is TodayItem.ShotsOnThisDay -> item.shots.take(1).flatMap { listOf(it.thumbnailUrl, it.fullUrl) } + item.entry?.coverUrl
    is TodayItem.SaleEndingSoon -> listOf(item.item.headerUrl)
    is TodayItem.GiveawayEndingSoon -> listOf(item.giveaway.imageUrl)
}

private fun icon(item: TodayItem): ImageVector = when (item) {
    is TodayItem.PlayedOnThisDay -> Icons.Default.History
    is TodayItem.FirstPlayedAnniversary -> Icons.Default.Cake
    is TodayItem.ShotsOnThisDay -> Icons.Default.PhotoLibrary
    is TodayItem.SaleEndingSoon -> Icons.Default.Sell
    is TodayItem.GiveawayEndingSoon -> Icons.Default.Redeem
}

@Composable
private fun kicker(item: TodayItem): String = when (item) {
    is TodayItem.PlayedOnThisDay -> pluralStringResource(R.plurals.today_years_ago, item.years, item.years)
    is TodayItem.ShotsOnThisDay -> pluralStringResource(R.plurals.today_years_ago, item.years, item.years)
    is TodayItem.FirstPlayedAnniversary -> pluralStringResource(R.plurals.today_anniversary, item.years, item.years)
    is TodayItem.SaleEndingSoon -> stringResource(R.string.today_sale_ending)
    is TodayItem.GiveawayEndingSoon -> stringResource(R.string.today_giveaway_ending)
}

@Composable
private fun headline(item: TodayItem): String = when (item) {
    is TodayItem.PlayedOnThisDay -> item.entry.title
    is TodayItem.FirstPlayedAnniversary -> item.entry.title
    is TodayItem.ShotsOnThisDay -> item.entry?.title ?: stringResource(R.string.today_shots_unknown)
    is TodayItem.SaleEndingSoon -> item.item.title
    is TodayItem.GiveawayEndingSoon -> item.giveaway.title
}

@Composable
private fun detail(item: TodayItem): String = when (item) {
    is TodayItem.PlayedOnThisDay -> stringResource(R.string.today_played, formatPlaytime(item.playtime))
    is TodayItem.FirstPlayedAnniversary -> stringResource(R.string.today_first_played)
    is TodayItem.ShotsOnThisDay -> pluralStringResource(R.plurals.today_shots, item.shots.size, item.shots.size)
    is TodayItem.SaleEndingSoon -> item.item.price?.let { stringResource(R.string.today_sale_price, it.discountPercent, it.priceText) }.orEmpty()
    is TodayItem.GiveawayEndingSoon -> stringResource(R.string.today_giveaway_free, item.giveaway.platformsText.ifBlank { item.giveaway.store.name })
}

private val ThumbSize = 52.dp
private const val RowAlpha = 0.5f
