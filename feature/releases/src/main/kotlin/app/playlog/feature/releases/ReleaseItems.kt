package app.playlog.feature.releases

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.playlog.core.data.ReleaseCard
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadge
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.model.UpcomingGame
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Sharp wide art first: Steam's 616px capsule, then the header the store gave. */
internal fun UpcomingGame.artUrls(): List<String?> =
    listOfNotNull(DealPick.steamCapsuleUrl(storeId).takeIf { platform == Platform.STEAM }) + headerUrl

@Composable
internal fun HighlightCard(card: ReleaseCard, today: LocalDate, onClick: () -> Unit, onToggleWishlist: () -> Unit) {
    val game = card.game
    Box(
        Modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .fillMaxWidth()
            .aspectRatio(HeroAspect)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        GameImage(urls = game.artUrls(), contentDescription = null, placeholderText = game.title, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Scrim))))
        Text(
            stringResource(R.string.releases_highlight).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = PlaylogColors.Background,
            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm).clip(CircleShape).background(PlaylogColors.Sky)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
        WishButton(card.wishlisted, onToggleWishlist, Modifier.align(Alignment.TopEnd).padding(Spacing.xxs), onImage = true)
        Column(Modifier.align(Alignment.BottomStart).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            game.date?.let { date ->
                Text(
                    listOfNotNull(relativeDay(date, today), longDate(date)).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = PlaylogColors.Sky,
                )
            }
            Text(game.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                MatchPill(card.match)
                card.sharedTags.forEach { Tag(it, highlighted = true) }
            }
        }
    }
}

/** Pinned row of the days that have releases; the day in view is filled. */
@Composable
internal fun DayStrip(days: List<Pair<LocalDate, Int>>, selected: Int, today: LocalDate, onSelect: (Int) -> Unit) {
    val strip = rememberLazyListState()
    LaunchedEffect(selected) { if (days.isNotEmpty()) strip.animateScrollToItem((selected - 2).coerceAtLeast(0)) }
    val locale = Locale.getDefault()
    LazyRow(
        state = strip,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = Spacing.xs),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        itemsIndexed(days, key = { _, d -> d.first.toEpochDay() }) { i, (date, count) ->
            val active = i == selected
            Column(
                Modifier
                    .width(DayWidth)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (active) PlaylogColors.Sky else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (date == today) PlaylogColors.Sky else Color.Transparent, MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button) { onSelect(i) }
                    .padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ink = if (active) PlaylogColors.Background else MaterialTheme.colorScheme.onSurface
                val soft = if (active) PlaylogColors.Background else MaterialTheme.colorScheme.onSurfaceVariant
                Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall, color = soft)
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, color = ink)
                Text(date.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale), style = MaterialTheme.typography.labelSmall, color = soft)
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(count.coerceAtMost(MaxDots)) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(if (active) PlaylogColors.Background else PlaylogColors.Sky))
                    }
                }
            }
        }
    }
}

@Composable
internal fun DayHeader(date: LocalDate, today: LocalDate) {
    Row(Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xs), verticalAlignment = Alignment.Bottom) {
        relativeDay(date, today)?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = PlaylogColors.Sky)
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(longDate(date), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun UndatedHeader() {
    Text(
        stringResource(R.string.releases_undated),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs),
    )
}

@Composable
internal fun ReleaseRow(card: ReleaseCard, onClick: () -> Unit, onToggleWishlist: () -> Unit, dateText: String? = null) {
    val game = card.game
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            GameImage(
                urls = game.artUrls(),
                contentDescription = null,
                placeholderText = game.title,
                modifier = Modifier.width(ThumbWidth).aspectRatio(ThumbAspect).clip(MaterialTheme.shapes.small),
            )
            if (game.platform != Platform.STEAM) PlatformBadge(game.platform, onImage = true, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            dateText?.let { Text(windowText(it), style = MaterialTheme.typography.bodySmall, color = PlaylogColors.Sky) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                if (card.match >= MatchShownFrom) MatchPill(card.match)
                val shown = card.sharedTags.ifEmpty { game.tags.take(2) }
                shown.take(2).forEach { Tag(it, highlighted = card.sharedTags.isNotEmpty()) }
            }
        }
        WishButton(card.wishlisted, onToggleWishlist)
    }
}

@Composable
private fun WishButton(wishlisted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onImage: Boolean = false) {
    IconButton(
        onClick = onClick,
        modifier = modifier.then(if (onImage) Modifier.clip(CircleShape).background(Scrim.copy(alpha = ScrimButtonAlpha)) else Modifier),
    ) {
        Icon(
            if (wishlisted) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = stringResource(if (wishlisted) R.string.releases_wish_remove else R.string.releases_wish_add),
            tint = if (wishlisted) PlaylogColors.Lime else if (onImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MatchPill(match: Float) {
    Text(
        stringResource(R.string.releases_match, (match * Percent).roundToInt()),
        style = MaterialTheme.typography.labelSmall,
        color = PlaylogColors.Background,
        modifier = Modifier.clip(CircleShape).background(PlaylogColors.Lime).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun Tag(text: String, highlighted: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (highlighted) PlaylogColors.Sky else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun windowText(text: String): String = when {
    text.equals("Coming soon", ignoreCase = true) -> stringResource(R.string.releases_soon)
    text.contains("announced", ignoreCase = true) -> stringResource(R.string.releases_tba)
    else -> text
}

private fun longDate(date: LocalDate): String = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()).format(date)

private val Scrim = Color(0xE6101018)
private const val ScrimButtonAlpha = 0.5f
private const val HeroAspect = 616f / 353f
private const val ThumbAspect = 616f / 353f
private val ThumbWidth = 112.dp
private val DayWidth = 56.dp
private const val MaxDots = 4
private const val MatchShownFrom = 0.5f
private const val Percent = 100
