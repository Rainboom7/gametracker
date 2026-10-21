package app.playlog.feature.deals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.Platform
import app.playlog.core.model.WishlistItem
import app.playlog.core.ui.DiscountBadge
import app.playlog.core.ui.formatShortDate

/** Wishlisted games, on-sale ones first, with a manual price check and Steam import. */
@Composable
internal fun WishlistContent(items: List<WishlistItem>, action: WishlistAction, actions: DealsActions) {
    val sorted = items.sortedWith(compareByDescending<WishlistItem> { it.price?.discountPercent ?: 0 }.thenByDescending { it.addedAt })
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = actions.onCheckPrices, enabled = action != WishlistAction.Working && items.isNotEmpty()) {
                        Text(stringResource(R.string.deals_wish_check))
                    }
                    OutlinedButton(onClick = actions.onImport, enabled = action != WishlistAction.Working) {
                        Text(stringResource(R.string.deals_wish_import))
                    }
                    if (action == WishlistAction.Working) {
                        CircularProgressIndicator(Modifier.padding(start = Spacing.xs).width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    }
                }
                val status = when (action) {
                    is WishlistAction.Checked -> stringResource(R.string.deals_wish_checked, action.drops)
                    is WishlistAction.Imported -> stringResource(R.string.deals_wish_imported, action.added)
                    else -> stringResource(R.string.deals_wish_hint)
                }
                Hint(status)
                if (items.any { it.platform == Platform.PLAYSTATION }) {
                    Hint(stringResource(R.string.deals_wish_ps_note))
                }
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") { Message(stringResource(R.string.deals_wish_empty), null, {}) }
        }
        items(sorted, key = { it.key }) { item -> WishRow(item, onClick = { actions.onOpenWish(item) }, onRemove = { actions.onRemoveWish(item) }) }
    }
}

@Composable
private fun WishRow(item: WishlistItem, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (item.isOnSale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameImage(
            urls = listOf(item.headerUrl),
            contentDescription = item.title,
            placeholderText = item.title,
            modifier = Modifier.width(ImageWidth).height(ImageHeight).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Spacing.xs))
                PlatformBadge(item.platform)
            }
            val price = item.price
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    item.comingSoon -> Hint(
                        item.releaseDate?.let { stringResource(R.string.deals_wish_coming_on, formatShortDate(it, withYear = true)) }
                            ?: stringResource(R.string.deals_wish_coming),
                    )
                    price == null -> Hint(stringResource(R.string.deals_wish_unknown))
                    price.discountPercent > 0 -> {
                        DiscountBadge(price.discountPercent)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(price.priceText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        price.originalPriceText?.let {
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough,
                            )
                        }
                    }
                    else -> Text(
                        price.priceText.ifBlank { stringResource(R.string.deals_wish_full) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item.checkedAt?.let {
                Text(
                    stringResource(R.string.deals_wish_checked_at, formatShortDate(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.deals_wish_remove)) }
    }
}

private val ImageWidth = 96.dp
private val ImageHeight = 45.dp
