package app.playlog.feature.deals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.playlog.core.data.CatalogGame
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadgeRow
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.WishMatcher
import app.playlog.core.ui.DealDetailSheet
import app.playlog.core.ui.DiscountBadge

/**
 * "В магазинах": games matching [query] in the Steam, PlayStation and Nintendo catalogues, for any
 * search screen to put under its own results. Tapping a game opens its store card; the heart uses the
 * shared wishlist, so the same game shows as wished everywhere.
 */
@Composable
fun CatalogSearchSection(query: String, modifier: Modifier = Modifier, viewModel: CatalogSearchViewModel = hiltViewModel()) {
    LaunchedEffect(query) { viewModel.setQuery(query) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wished by viewModel.wished.collectAsStateWithLifecycle()
    val openDeal by viewModel.openDeal.collectAsStateWithLifecycle()
    val lowest by viewModel.lowest.collectAsStateWithLifecycle()
    when (val s = state) {
        CatalogState.Idle -> Unit
        CatalogState.Loading -> Row(modifier.padding(vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                stringResource(R.string.catalog_searching),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        is CatalogState.Ready -> Column(modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(stringResource(R.string.catalog_title))
            val games = s.games.filterNot { it.owned }
            if (games.isEmpty()) Hint(stringResource(R.string.catalog_empty, s.query))
            games.forEach { game -> CatalogRow(game, wished, onOpen = { viewModel.open(game.main) }, onWish = { viewModel.toggleWish(game.main) }) }
        }
    }
    openDeal?.let { pick ->
        DealDetailSheet(pick, onDismiss = { viewModel.open(null) }, wished = pick in wished, onToggleWish = { viewModel.toggleWish(pick) }, lowest = lowest)
    }
}

@Composable
private fun CatalogRow(game: CatalogGame, wished: WishMatcher, onOpen: () -> Unit, onWish: () -> Unit) {
    val sale = game.offers.filter { it.isOnSale }.maxByOrNull { it.discountPercent }
    val shown = sale ?: game.offers.firstOrNull { it.priceText.isNotBlank() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameImage(
            urls = game.offers.map { it.headerUrl },
            contentDescription = null,
            placeholderText = game.title,
            modifier = Modifier.width(ImageWidth).height(ImageHeight).clip(MaterialTheme.shapes.small),
        )
        Column(Modifier.weight(1f).padding(horizontal = Spacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            PlatformBadgeRow(game.platforms)
            if (shown != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (shown.isOnSale) DiscountBadge(shown.discountPercent)
                    Text(
                        shown.priceText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (shown.isOnSale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val isWished = game.main in wished
        IconButton(onClick = onWish) {
            Icon(
                if (isWished) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(if (isWished) R.string.deals_wish_remove else R.string.deals_wish_add),
                tint = if (isWished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ImageWidth = 96.dp
private val ImageHeight = 45.dp
