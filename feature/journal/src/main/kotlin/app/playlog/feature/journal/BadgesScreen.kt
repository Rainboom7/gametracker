package app.playlog.feature.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.component.Skeleton
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.Badge
import app.playlog.core.model.MilestoneKind
import app.playlog.core.ui.formatShortDate

@Composable
fun BadgesRoute(onBack: () -> Unit, viewModel: BadgesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state is BadgesUiState.Ready) { if (state is BadgesUiState.Ready) viewModel.markSeen() }
    BadgesScreen(state, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(state: BadgesUiState, onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<MilestoneKind?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.badges_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.badges_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when (state) {
            BadgesUiState.Loading -> Skeleton(Modifier.padding(padding).padding(Spacing.lg).fillMaxWidth().height(SkeletonHeight))
            is BadgesUiState.Ready -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Summary(state) }
                if (state.recent.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.badges_recent)) }
                    items(state.recent, key = { "recent-${it.kind}" }) { BadgeCard(it, onClick = { open = it.kind }) }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.badges_all)) }
                }
                items(state.badges.filterNot { it.isRecent }, key = { it.kind }) { BadgeCard(it, onClick = { open = it.kind }) }
            }
        }
    }
    val sheet = (state as? BadgesUiState.Ready)?.badges?.find { it.kind == open }
    if (sheet != null) {
        ModalBottomSheet(onDismissRequest = { open = null }) { BadgeLadder(sheet) }
    }
}

@Composable
private fun Summary(state: BadgesUiState.Ready) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.badges_summary, state.unlocked, state.badges.size), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.badges_summary_levels, state.levels, state.maxLevels),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (state.maxLevels == 0) 0f else state.levels.toFloat() / state.maxLevels },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraSmall),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun BadgeCard(badge: Badge, onClick: () -> Unit) {
    val earned = badge.level > 0
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (badge.isRecent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            BadgeMedal(badge, size = MedalSize)
            Text(stringResource(badgeTitle(badge.kind)), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                badgeValue(badge.kind, badge.milestone.value),
                style = MaterialTheme.typography.headlineSmall,
                color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(badgeDescription(badge.kind)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                caption(badge),
                style = MaterialTheme.typography.labelSmall,
                color = if (badge.isRecent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun caption(badge: Badge): String {
    val unlockedAt = badge.unlockedAt
    val next = badge.milestone.nextTarget
    return when {
        badge.isRecent && unlockedAt != null -> stringResource(R.string.badges_unlocked_on, badge.level, formatShortDate(unlockedAt))
        badge.isRecent -> stringResource(R.string.badges_new)
        next == null -> stringResource(R.string.badges_maxed)
        else -> stringResource(R.string.badges_next, badgeValue(badge.kind, next))
    }
}

/** Every level of one badge, reached ones ticked, so the next goal is concrete. */
@Composable
private fun BadgeLadder(badge: Badge) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BadgeMedal(badge, size = SheetMedalSize)
            Column {
                Text(stringResource(badgeTitle(badge.kind)), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(badgeDescription(badge.kind)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(badgeValue(badge.kind, badge.milestone.value), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        badge.milestone.thresholds.forEachIndexed { index, target ->
            val reached = index < badge.level
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(
                    if (reached) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
                Text(stringResource(R.string.badges_level, index + 1), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(badgeValue(badge.kind, target), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}

private val MedalSize = 72.dp
private val SheetMedalSize = 88.dp
private val SkeletonHeight = 160.dp
