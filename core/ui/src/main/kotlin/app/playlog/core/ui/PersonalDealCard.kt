package app.playlog.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import kotlin.math.roundToInt

/**
 * A "for you" deal: sharp wide art with the title on it, how well it matches the user's taste,
 * why (the games it resembles), the tags it shares with them, and the price.
 */
@Composable
fun PersonalDealCard(pick: DealPick, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(CardWidth)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(ArtAspect)) {
            GameImage(
                urls = dealArt(pick),
                contentDescription = stringResource(R.string.cover_description, pick.title),
                placeholderText = pick.title,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(ScrimFrom to Color.Transparent, 1f to Scrim)))
            if (pick.isOnSale) DiscountBadge(pick.discountPercent, Modifier.align(Alignment.TopStart).padding(8.dp))
            if (pick.match > 0f) {
                Text(
                    stringResource(R.string.deal_match_short, (pick.match * Percent).roundToInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = PlaylogColors.Background,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(PlaylogColors.Lime)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                pick.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                dealReason(pick),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val tags = pick.sharedTags.ifEmpty { pick.tags }.take(MaxTags)
            if (tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { tag ->
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = PlaylogColors.Sky,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            DealPrice(pick)
        }
    }
}

/** Steam's 616px capsule first (header.jpg is 460px and soft on a wide card), then what the store gave. */
fun dealArt(pick: DealPick): List<String?> = listOf(
    DealPick.steamCapsuleUrl(pick.storeId).takeIf { pick.platform == Platform.STEAM },
    pick.headerUrl,
    pick.screenshots.firstOrNull(),
)

private val CardWidth = 288.dp
private const val ArtAspect = 616f / 353f
private val Scrim = Color(0xE6101018)
private const val ScrimFrom = 0.45f
private const val MaxTags = 3
private const val Percent = 100
