package app.playlog.feature.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.SecondaryButton
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.AskAnswer
import app.playlog.core.model.AskIntent
import app.playlog.core.model.DealPick
import app.playlog.core.model.GameId
import app.playlog.core.model.PlayFeature
import app.playlog.core.ui.DealCard
import app.playlog.core.ui.DealDetailSheet
import app.playlog.core.ui.LibraryListRow
import app.playlog.core.ui.platformName

@Composable
fun AskRoute(onBack: () -> Unit, onOpenGame: (GameId) -> Unit, viewModel: AskViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AskScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onAsk = viewModel::ask,
        onClear = viewModel::clear,
        onOpenGame = onOpenGame,
        onOpenDeal = viewModel::openDeal,
    )
    state.openDeal?.let { DealDetailSheet(it, onDismiss = { viewModel.openDeal(null) }) }
}

@Composable
fun AskScreen(
    state: AskUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
    onOpenGame: (GameId) -> Unit,
    onOpenDeal: (DealPick) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Header(onBack) }
        item { QueryField(state.query, onQueryChange, onAsk = { onAsk(state.query) }, onClear = onClear) }
        when (val result = state.result) {
            AskResult.Idle -> item { Examples(onAsk) }
            AskResult.Thinking -> item { Thinking() }
            is AskResult.Failed -> item { Failed(result.message, onRetry = { onAsk(state.query) }) }
            is AskResult.Answer -> answer(result.answer, onOpenGame, onOpenDeal)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.answer(
    answer: AskAnswer,
    onOpenGame: (GameId) -> Unit,
    onOpenDeal: (DealPick) -> Unit,
) {
    item { Understood(answer) }
    if (answer.owned.isEmpty() && answer.store.isEmpty()) {
        item { Note(stringResource(R.string.ask_nothing)) }
        return
    }
    if (answer.owned.isNotEmpty()) {
        item { SectionTitle(stringResource(R.string.ask_owned), PlaylogColors.Lime, answer.owned.size) }
        items(answer.owned, key = { "own:" + it.key }) { entry ->
            LibraryListRow(entry, onClick = { onOpenGame(entry.primary.id) })
        }
    }
    if (answer.store.isNotEmpty()) {
        item {
            Column {
                SectionTitle(stringResource(R.string.ask_store), PlaylogColors.Sky, answer.store.size)
                Text(stringResource(R.string.ask_store_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(answer.store, key = { "store:" + it.key }) { pick ->
            DealCard(pick, onClick = { onOpenDeal(pick) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ask_back)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ask_title), style = MaterialTheme.typography.headlineMedium)
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PlaylogColors.Violet, modifier = Modifier.padding(start = Spacing.xs))
            }
            Text(stringResource(R.string.ask_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Search field edged in the violet-to-pink "magic" gradient used for everything Ask. */
@Composable
private fun QueryField(query: String, onQueryChange: (String) -> Unit, onAsk: () -> Unit, onClear: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.ask_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onAsk() }),
        trailingIcon = {
            Row {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ask_clear)) }
                }
                IconButton(onClick = onAsk) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.ask_send), tint = PlaylogColors.Violet)
                }
            }
        },
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Brush.linearGradient(listOf(PlaylogColors.Violet, PlaylogColors.Pink)), shape),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Examples(onAsk: (String) -> Unit) {
    val accents = listOf(PlaylogColors.Violet, PlaylogColors.Sky, PlaylogColors.Pink, PlaylogColors.Orange, PlaylogColors.Lime, PlaylogColors.Mint)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.ask_examples), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            stringArrayResource(R.array.ask_example_queries).forEachIndexed { i, example ->
                val accent = accents[i % accents.size]
                Text(
                    example,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(accent.copy(alpha = ChipAlpha))
                        .clickable(role = Role.Button) { onAsk(example) }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun Thinking() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CircularProgressIndicator(Modifier.size(20.dp), color = PlaylogColors.Violet, strokeWidth = 2.dp)
        Text(stringResource(R.string.ask_thinking), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Failed(reason: String?, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Note(stringResource(R.string.ask_failed))
        // The technical cause, small: enough for a screenshot to say what broke.
        reason?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        SecondaryButton(stringResource(R.string.ask_retry), onClick = onRetry)
    }
}

/** "Понял так": every recognized piece of the request as a colored chip, so the parsing is visible. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Understood(answer: AskAnswer) {
    val chips = understoodChips(answer)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(if (chips.isEmpty()) R.string.ask_not_understood else R.string.ask_understood),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            chips.forEach { (label, accent) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(accent)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun understoodChips(answer: AskAnswer): List<Pair<String, Color>> {
    val intent: AskIntent = answer.intent
    val chips = mutableListOf<Pair<String, Color>>()
    intent.features.forEach { chips += featureName(it) to PlaylogColors.Sky }
    intent.players?.let { chips += stringResource(R.string.ask_players, it) to PlaylogColors.Sky }
    intent.tags.filterNot { it in ModeTags }.forEach { chips += it to PlaylogColors.Violet }
    intent.similarTo?.let { title ->
        val label = if (answer.similarTags.isEmpty()) {
            stringResource(R.string.ask_like, title)
        } else {
            stringResource(R.string.ask_like_tags, title, answer.similarTags.joinToString(", "))
        }
        chips += label to PlaylogColors.Pink
    }
    intent.platforms.forEach { chips += platformName(it) to PlaylogColors.Mint }
    if (intent.ownedOnly) chips += stringResource(R.string.ask_owned_only) to PlaylogColors.Lime
    if (intent.unplayed) chips += stringResource(R.string.ask_unplayed) to PlaylogColors.Lime
    if (intent.free) chips += stringResource(R.string.ask_free) to PlaylogColors.Orange
    if (intent.onSale) chips += stringResource(R.string.ask_on_sale) to PlaylogColors.Orange
    return chips
}

@Composable
private fun featureName(feature: PlayFeature): String = stringResource(
    when (feature) {
        PlayFeature.SPLIT_SCREEN_COOP -> R.string.ask_feature_SPLIT_SCREEN_COOP
        PlayFeature.SHARED_SCREEN -> R.string.ask_feature_SHARED_SCREEN
        PlayFeature.LOCAL_PVP -> R.string.ask_feature_LOCAL_PVP
        PlayFeature.ONLINE_COOP -> R.string.ask_feature_ONLINE_COOP
        PlayFeature.COOP -> R.string.ask_feature_COOP
        PlayFeature.PVP -> R.string.ask_feature_PVP
        PlayFeature.SINGLE_PLAYER -> R.string.ask_feature_SINGLE_PLAYER
        PlayFeature.REMOTE_PLAY_TOGETHER -> R.string.ask_feature_REMOTE_PLAY_TOGETHER
        PlayFeature.CONTROLLER -> R.string.ask_feature_CONTROLLER
    },
)

@Composable
private fun SectionTitle(title: String, accent: Color, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.clip(MaterialTheme.shapes.extraLarge).background(accent).padding(horizontal = Spacing.xs, vertical = 1.dp),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Play modes already show as feature chips; repeating them as tags would read twice. */
private val ModeTags = setOf("Local Co-Op", "Co-op", "Online Co-Op", "PvP")
private const val ChipAlpha = 0.16f
