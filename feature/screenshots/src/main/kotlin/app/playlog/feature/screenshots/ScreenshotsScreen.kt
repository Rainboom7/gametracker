package app.playlog.feature.screenshots

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.GameImage
import app.playlog.core.designsystem.component.InfoBanner
import app.playlog.core.designsystem.component.PlatformFilterChip
import app.playlog.core.designsystem.component.PlaylogIconButton
import app.playlog.core.designsystem.component.SecondaryButton
import app.playlog.core.designsystem.theme.LocalPlatformPalette
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.model.Screenshot
import app.playlog.core.ui.ScreenshotTile
import app.playlog.core.ui.formatRelativeDate
import app.playlog.core.ui.platformName
import java.time.Instant
import java.time.ZoneId

@Composable
fun ScreenshotsRoute(
    onOpenGame: (GameId) -> Unit,
    onClearFilter: () -> Unit,
    viewModel: ScreenshotsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        pendingImport = uris
    }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    ScreenshotsScreen(
        state = state,
        onImport = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onDismissBanner = viewModel::dismissNintendoBanner,
        onOpen = { shot -> viewerIndex = state.all.indexOfFirst { it.id == shot.id }.takeIf { it >= 0 } },
        onClearFilter = onClearFilter,
    )

    if (pendingImport.isNotEmpty()) {
        ImportPlatformDialog(
            onPick = { platform ->
                viewModel.import(pendingImport.map { readCapture(context, it) }, platform)
                pendingImport = emptyList()
            },
            onDismiss = { pendingImport = emptyList() },
        )
    }
    viewerIndex?.let { start ->
        ScreenshotViewer(
            screenshots = state.all,
            startIndex = start,
            onDismiss = { viewerIndex = null },
            onOpenGame = { id ->
                viewerIndex = null
                onOpenGame(id)
            },
            onDelete = { shot ->
                viewModel.delete(shot.id)
                viewerIndex = null
            },
        )
    }
}

@Composable
fun ScreenshotsScreen(
    state: ScreenshotsUiState,
    onImport: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpen: (Screenshot) -> Unit,
    onClearFilter: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.filterEntry?.title ?: stringResource(R.string.screenshots_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                PlaylogIconButton(Icons.Default.AddPhotoAlternate, stringResource(R.string.screenshots_import), onImport)
            }
        }
        if (state.filterEntry != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row { PlatformFilterChip(stringResource(R.string.screenshots_filter_clear), selected = false, onClick = onClearFilter) }
            }
        }
        if (state.showNintendoBanner) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                InfoBanner(
                    text = stringResource(R.string.screenshots_nintendo_banner),
                    background = LocalPlatformPalette.current.nintendo.badgeBg,
                    border = Color(0xFF4A2529),
                    action = { SecondaryButton(stringResource(R.string.screenshots_import), onClick = onImport) },
                    onDismiss = onDismissBanner,
                    dismissLabel = stringResource(R.string.screenshots_dismiss),
                )
            }
        }
        if (!state.isLoading && state.groups.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.screenshots_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.xl),
                )
            }
        }
        state.groups.forEach { group ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(
                            group.date?.let { formatRelativeDate(it.atStartOfDay(ZoneId.systemDefault()).toInstant()) },
                            group.entry?.title ?: stringResource(R.string.screenshots_unknown_game),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.screenshots_count, group.screenshots.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(group.screenshots, key = { it.id }) { shot ->
                ScreenshotTile(shot, onClick = { onOpen(shot) }, title = group.entry?.title)
            }
        }
    }
}

@Composable
private fun ImportPlatformDialog(onPick: (Platform) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screenshots_import_platform)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Platform.entries.forEach { p ->
                    PlatformFilterChip(platformName(p), selected = false, onClick = { onPick(p) }, platform = p)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.screenshots_close)) } },
    )
}

@Composable
private fun ScreenshotViewer(
    screenshots: List<Screenshot>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onOpenGame: (GameId) -> Unit,
    onDelete: (Screenshot) -> Unit,
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = startIndex) { screenshots.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val shot = screenshots[page]
                GameImage(
                    urls = listOf(shot.fullUrl, shot.thumbnailUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                )
            }
            val current = screenshots.getOrNull(pager.currentPage)
            Row(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.screenshots_close), tint = Color.White)
                }
                Row {
                    if (current != null) {
                        IconButton(onClick = { share(context, current) }) {
                            Icon(Icons.Default.Share, stringResource(R.string.screenshots_share), tint = Color.White)
                        }
                        current.gameId?.let { id ->
                            IconButton(onClick = { onOpenGame(id) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.screenshots_open_game), tint = Color.White)
                            }
                        }
                        IconButton(onClick = { onDelete(current) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.screenshots_delete), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun share(context: Context, shot: Screenshot) {
    val intent = if (shot.fullUrl.startsWith("content://")) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(shot.fullUrl))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shot.fullUrl)
        }
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** Keeps read access across restarts and pulls the capture date when the provider exposes it. */
private fun readCapture(context: Context, uri: Uri): PickedCapture {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    var takenAt: Instant? = null
    var width: Int? = null
    var height: Int? = null
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT),
            null,
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                c.getLong(0).takeIf { it > 0 }?.let { takenAt = Instant.ofEpochMilli(it) }
                width = c.getInt(1).takeIf { it > 0 }
                height = c.getInt(2).takeIf { it > 0 }
            }
        }
    }
    return PickedCapture(uri.toString(), takenAt, width, height)
}
