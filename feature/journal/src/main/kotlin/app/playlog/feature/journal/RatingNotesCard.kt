package app.playlog.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.GameNote
import app.playlog.core.model.LibraryEntry

/**
 * The user's own 1..10 rating and private notes for a game, saved on the device as they type.
 * Self-contained: drop it into the game page with just the entry.
 */
@Composable
fun RatingNotesCard(
    entry: LibraryEntry,
    modifier: Modifier = Modifier,
    viewModel: GameNotesViewModel = hiltViewModel(key = "notes-${entry.key}"),
) {
    LaunchedEffect(entry.key) { viewModel.bind(entry.key) }
    DisposableEffect(viewModel) { onDispose { viewModel.commitText() } }
    val note by viewModel.note.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    RatingNotesContent(
        note = note,
        text = draft ?: note?.text.orEmpty(),
        onRate = viewModel::setRating,
        onText = viewModel::editText,
        onCommit = viewModel::commitText,
        modifier = modifier,
    )
}

@Composable
fun RatingNotesContent(
    note: GameNote?,
    text: String,
    onRate: (Int?) -> Unit,
    onText: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rating = note?.rating
    val verdicts = stringArrayResource(R.array.rating_verdicts)
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notes_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    rating?.let { "$it/10 · ${verdicts[it - 1]}" } ?: stringResource(R.string.notes_unrated),
                    style = MaterialTheme.typography.labelLarge,
                    color = rating?.let { ratingColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                GameNote.RATING_RANGE.forEach { value ->
                    RatingDot(
                        value = value,
                        filled = rating != null && value <= rating,
                        current = value == rating,
                        onClick = { onRate(if (value == rating) null else value) },
                        label = stringResource(R.string.notes_rate, value),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onCommit() },
                placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                minLines = 2,
                maxLines = MaxNoteLines,
            )
            Text(stringResource(R.string.notes_private), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RatingDot(value: Int, filled: Boolean, current: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val color = ratingColor(value)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (filled) color.copy(alpha = if (current) 1f else FilledAlpha) else Color.Transparent)
            .border(1.dp, if (filled) color else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = current
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Warm for low scores, the lime accent at the top. */
@Composable
private fun ratingColor(value: Int): Color {
    val fraction = (value - 1f) / (GameNote.RATING_RANGE.last - 1f)
    val low = MaterialTheme.colorScheme.error
    val high = MaterialTheme.colorScheme.primary
    return lerp(low, high, fraction)
}

private const val MaxNoteLines = 8
private const val FilledAlpha = 0.55f
