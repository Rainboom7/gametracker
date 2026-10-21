package app.playlog.feature.discover

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.PlatformBadgeRow
import app.playlog.core.designsystem.component.PlatformFilterChip
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.DiscoverSummary
import app.playlog.core.model.GameId
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.PickReason
import app.playlog.core.model.Platform
import app.playlog.core.model.RouletteMood
import app.playlog.core.ui.entryPlaytimeText
import app.playlog.core.ui.platformName
import kotlinx.coroutines.delay

/** Covers the reel flicks through while spinning: the backlog and forgotten shelves. */
internal fun DiscoverSummary.reel(): List<LibraryEntry> = shelves
    .filter { it.reason == PickReason.BACKLOG || it.reason == PickReason.RETURN_TO }
    .flatMap { shelf -> shelf.picks.map { it.entry } }

@Composable
internal fun RouletteCard(
    state: RouletteState,
    backlogCount: Int,
    reel: List<LibraryEntry>,
    onSpin: () -> Unit,
    onSelectPlatform: (Platform?) -> Unit,
    onSelectMood: (RouletteMood) -> Unit,
    onOpen: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpinningDie(state.spinning)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.discover_roulette_title), style = MaterialTheme.typography.titleLarge)
            }
            Text(
                stringResource(R.string.discover_roulette_hint, backlogCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                RouletteMood.entries.forEach { mood ->
                    FilterChip(selected = state.mood == mood, onClick = { onSelectMood(mood) }, label = { Text(stringResource(moodLabel(mood))) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PlatformFilterChip(platformName(null), selected = state.platform == null, onClick = { onSelectPlatform(null) })
                Platform.entries.forEach { p ->
                    PlatformFilterChip(platformName(p), selected = state.platform == p, onClick = { onSelectPlatform(p) }, platform = p)
                }
            }
            AnimatedContent(
                targetState = state,
                contentKey = { if (it.spinning) -1 else it.spins },
                transitionSpec = { (fadeIn() + scaleIn(initialScale = RevealScale)) togetherWith fadeOut() },
                label = "roulette",
            ) { s ->
                when {
                    s.spinning && reel.isNotEmpty() -> Reel(reel)
                    s.result != null -> RouletteResult(s.result, onClick = { onOpen(s.result.primary.id) })
                    s.empty -> Text(stringResource(R.string.discover_roulette_empty), style = MaterialTheme.typography.bodyMedium)
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
            Button(onClick = onSpin, enabled = !state.spinning, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.result == null) R.string.discover_roulette_spin else R.string.discover_roulette_again))
            }
        }
    }
}

@Composable
private fun SpinningDie(spinning: Boolean) {
    var angle by remember { mutableIntStateOf(0) }
    LaunchedEffect(spinning) {
        while (spinning) {
            angle = (angle + DieStep) % FullTurn
            delay(ReelFrameMillis)
        }
    }
    Icon(Icons.Default.Casino, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.rotate(angle.toFloat()))
}

/** A fast flicker through real covers from the pool, slot-machine style. */
@Composable
private fun Reel(entries: List<LibraryEntry>) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(entries) {
        while (true) {
            delay(ReelFrameMillis)
            index = (index + 1) % entries.size
        }
    }
    val entry = entries[index % entries.size]
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameImage(
            urls = listOf(entry.coverUrl, entry.fallbackCoverUrl),
            contentDescription = null,
            placeholderText = entry.title,
            modifier = Modifier.size(width = CoverWidth, height = CoverHeight).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            entry.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RouletteResult(entry: LibraryEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameImage(
            urls = listOf(entry.coverUrl, entry.fallbackCoverUrl),
            contentDescription = null,
            placeholderText = entry.title,
            modifier = Modifier.size(width = CoverWidth, height = CoverHeight).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entryPlaytimeText(entry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            PlatformBadgeRow(entry.platforms)
        }
        FilledTonalButton(onClick = onClick) { Text(stringResource(R.string.discover_open)) }
    }
}

@StringRes
private fun moodLabel(mood: RouletteMood): Int = when (mood) {
    RouletteMood.ANY -> R.string.roulette_mood_ANY
    RouletteMood.FORGOTTEN -> R.string.roulette_mood_FORGOTTEN
    RouletteMood.UNTOUCHED -> R.string.roulette_mood_UNTOUCHED
    RouletteMood.COMFORT -> R.string.roulette_mood_COMFORT
}

/** "Случайная игра": the roulette on its own screen, spun on arrival (launcher shortcut). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouletteRoute(onBack: () -> Unit, onOpenGame: (GameId) -> Unit, viewModel: DiscoverViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val content = state as? DiscoverUiState.Content
    var spunOnArrival by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(content != null) {
        if (content != null && !spunOnArrival) {
            spunOnArrival = true
            viewModel.spin()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.roulette_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.roulette_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.lg)) {
            if (content != null) {
                RouletteCard(
                    state = content.roulette,
                    backlogCount = content.summary.backlogCount,
                    reel = content.summary.reel(),
                    onSpin = viewModel::spin,
                    onSelectPlatform = viewModel::selectRoulettePlatform,
                    onSelectMood = viewModel::selectRouletteMood,
                    onOpen = onOpenGame,
                )
            }
        }
    }
}

private const val RevealScale = 0.92f
private const val ReelFrameMillis = 80L
private const val DieStep = 45
private const val FullTurn = 360
private val CoverWidth = 60.dp
private val CoverHeight = 80.dp
