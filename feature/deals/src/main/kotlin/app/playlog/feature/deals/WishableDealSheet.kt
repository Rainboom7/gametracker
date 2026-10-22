package app.playlog.feature.deals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.model.DealPick
import app.playlog.core.model.PriceLow
import app.playlog.core.ui.DealDetailSheet

/** [DealDetailSheet] wired to the shared wishlist and price history, for screens that only show deals. */
@Composable
fun WishableDealSheet(pick: DealPick, onDismiss: () -> Unit, viewModel: WishSheetViewModel = hiltViewModel()) {
    val wished by viewModel.wished.collectAsStateWithLifecycle()
    var lowest by remember(pick.key) { mutableStateOf<PriceLow?>(null) }
    LaunchedEffect(pick.key) { lowest = viewModel.lowest(pick) }
    DealDetailSheet(pick, onDismiss = onDismiss, wished = pick in wished, onToggleWish = { viewModel.toggle(pick) }, lowest = lowest)
}
