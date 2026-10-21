package app.playlog.feature.deals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.playlog.core.designsystem.theme.Spacing
import kotlin.math.ceil

/** Discount, price ceiling, genres and taste; changes apply live so the grid behind updates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    filter: DealsFilter,
    genres: List<String>,
    maxPrice: Double?,
    hasTaste: Boolean,
    onFilter: (DealsFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.deals_filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onFilter(DealsFilter(sort = filter.sort)) }) { Text(stringResource(R.string.deals_filters_reset)) }
            }

            Label(stringResource(R.string.deals_filter_discount))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DiscountSteps.forEach { step ->
                    FilterChip(
                        selected = filter.minDiscount == step,
                        onClick = { onFilter(filter.copy(minDiscount = step)) },
                        label = { Text(if (step == 0) stringResource(R.string.deals_filter_discount_any) else "−$step%") },
                    )
                }
            }

            if (maxPrice != null && maxPrice > 0) PriceSlider(filter, maxPrice, onFilter)

            Label(stringResource(R.string.deals_filter_genres))
            if (genres.isEmpty()) {
                Hint(stringResource(R.string.deals_filter_genres_none))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    genres.forEach { genre ->
                        val on = genre in filter.genres
                        FilterChip(
                            selected = on,
                            onClick = { onFilter(filter.copy(genres = if (on) filter.genres - genre else filter.genres + genre)) },
                            label = { Text(genre) },
                        )
                    }
                }
            }

            if (hasTaste) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.deals_filter_similar), style = MaterialTheme.typography.bodyLarge)
                        Hint(stringResource(R.string.deals_filter_similar_hint))
                    }
                    Switch(checked = filter.similarOnly, onCheckedChange = { onFilter(filter.copy(similarOnly = it)) })
                }
            }

            Spacer(Modifier)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.deals_filters_done)) }
        }
    }
}

/** Ceiling from 0 to the most expensive loaded deal; the far right means "any price". */
@Composable
private fun PriceSlider(filter: DealsFilter, maxPrice: Double, onFilter: (DealsFilter) -> Unit) {
    val top = ceil(maxPrice).toFloat()
    var value by remember(top) { mutableFloatStateOf(filter.maxPrice?.toFloat()?.coerceAtMost(top) ?: top) }
    val any = value >= top
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label(stringResource(R.string.deals_filter_price), Modifier.weight(1f))
        Text(
            if (any) stringResource(R.string.deals_filter_price_any) else stringResource(R.string.deals_filter_price_value, formatAmount(value)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onFilter(filter.copy(maxPrice = if (value >= top) null else value.toDouble())) },
        valueRange = 0f..top,
    )
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier)
}

private fun formatAmount(value: Float): String =
    if (value >= RoundAbove) String.format(java.util.Locale.getDefault(), "%,.0f", value) else String.format(java.util.Locale.getDefault(), "%.2f", value)

private val DiscountSteps = listOf(0, 25, 50, 75, 90)
private const val RoundAbove = 100f
