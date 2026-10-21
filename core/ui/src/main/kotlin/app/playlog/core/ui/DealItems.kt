package app.playlog.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadge
import app.playlog.core.model.DealPick
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Store capsule with the discount, price and why it was picked; 260dp wide for horizontal rows. */
@Composable
fun DealCard(pick: DealPick, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(260.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box {
            GameImage(
                urls = listOf(pick.headerUrl, pick.screenshots.firstOrNull()),
                contentDescription = stringResource(R.string.cover_description, pick.title),
                placeholderText = pick.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(HeaderAspect),
            )
            DiscountBadge(pick.discountPercent, Modifier.align(Alignment.TopStart).padding(8.dp))
            PlatformBadge(pick.platform, onImage = true, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(pick.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                pick.endsAt?.takeIf { pick.match == 0f }?.let { stringResource(R.string.deal_ends, formatShortDate(it)) } ?: dealReason(pick),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            DealPrice(pick)
        }
    }
}

@Composable
fun DiscountBadge(percent: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text("−$percent%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun DealPrice(pick: DealPick, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(pick.priceText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        pick.originalPriceText?.let { original ->
            Spacer(Modifier.width(8.dp))
            Text(
                original,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        pick.reviewPercent?.let {
            Text(
                stringResource(R.string.deal_reviews, it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** "Похоже на Hades и Celeste", or the shared tags when no single game stands out. */
@Composable
fun dealReason(pick: DealPick): String = when {
    pick.becauseOf.size >= 2 -> stringResource(R.string.deal_like_two, pick.becauseOf[0], pick.becauseOf[1])
    pick.becauseOf.size == 1 -> stringResource(R.string.deal_like_one, pick.becauseOf[0])
    pick.sharedTags.isNotEmpty() -> stringResource(R.string.deal_like_tags, pick.sharedTags.joinToString(", "))
    pick.match > 0f -> stringResource(R.string.deal_like_taste)
    else -> stringResource(R.string.deal_on_sale)
}

/** "29 окт." in the device locale. */
internal fun formatShortDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(instant.atZone(zone))

private const val HeaderAspect = 460f / 215f
