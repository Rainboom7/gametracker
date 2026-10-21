package app.playlog.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadge
import app.playlog.core.model.DealPick

/** Screenshots, price, why it was picked, description and a link to the store page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailSheet(pick: DealPick, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Screenshots(pick)
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pick.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    PlatformBadge(pick.platform)
                    Spacer(Modifier.width(8.dp))
                    DiscountBadge(pick.discountPercent)
                }
                DealPrice(pick)
                pick.endsAt?.let { ends ->
                    IconLine(Icons.Default.Schedule, stringResource(R.string.deal_ends, formatShortDate(ends)))
                }
                if (pick.becauseOf.isNotEmpty() || pick.sharedTags.isNotEmpty()) {
                    IconLine(Icons.Default.LocalOffer, dealReason(pick), highlight = true)
                }
                if (pick.match > 0f) {
                    Text(
                        stringResource(R.string.deal_match, (pick.match * Percent).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (pick.sharedTags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pick.sharedTags.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                    }
                }
                pick.shortDescription?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = { uriHandler.openUri(pick.storeUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.deal_open_store, platformName(pick.platform)))
                }
            }
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, highlight: Boolean = false) {
    val color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun Screenshots(pick: DealPick) {
    val images = pick.screenshots.ifEmpty { listOf(pick.headerUrl) }
    val pager = rememberPagerState { images.size }
    Box {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 8.dp) { page ->
            GameImage(
                urls = listOf(images[page], pick.headerUrl),
                contentDescription = stringResource(R.string.deal_screenshot, page + 1, pick.title),
                modifier = Modifier.fillMaxWidth().aspectRatio(ScreenshotAspect).clip(MaterialTheme.shapes.medium),
            )
        }
        if (images.size > 1) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(images.size) { i ->
                    val current = i == pager.currentPage
                    Box(
                        Modifier
                            .size(if (current) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = DotAlpha)),
                    )
                }
            }
        }
    }
}

private const val ScreenshotAspect = 16f / 9f
private const val Percent = 100
private const val DotAlpha = 0.5f
