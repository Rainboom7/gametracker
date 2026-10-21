package app.playlog.feature.recap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.GameId
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RecapRoute(onClose: () -> Unit, onOpenGame: (GameId) -> Unit, viewModel: RecapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecapScreen(state, onClose, onOpenGame)
}

/** Full-screen stories: tap the right side for the next card, the left for the previous one, or swipe. */
@Composable
fun RecapScreen(state: RecapUiState, onClose: () -> Unit, onOpenGame: (GameId) -> Unit) {
    val story = state as? RecapUiState.Story
    if (story == null) {
        Box(Modifier.fillMaxSize().background(PlaylogColors.Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PlaylogColors.Lime)
        }
        return
    }
    val pages = story.pages
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(PlaylogColors.Background)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
            val colors = PageGradients[index % PageGradients.size]
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors))
                    .pointerInput(pages.size) {
                        detectTapGestures { offset ->
                            val target = if (offset.x < size.width * BackZone) index - 1 else index + 1
                            if (target in pages.indices) scope.launch { pager.animateScrollToPage(target) }
                        }
                    },
            ) {
                RecapPageContent(pages[index], active = pager.settledPage == index, onOpenGame = onOpenGame)
            }
        }
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                pages.indices.forEach { i ->
                    val fill by animateFloatAsState(if (i <= pager.currentPage) 1f else 0f, tween(SegmentMillis), label = "segment")
                    Box(Modifier.weight(1f).height(SegmentHeight).clip(SegmentShape).background(Color.White.copy(alpha = TrackAlpha))) {
                        Box(Modifier.fillMaxWidth(fill).height(SegmentHeight).background(Color.White))
                    }
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.recap_close), tint = Color.White, modifier = Modifier.size(CloseSize))
            }
        }
    }
}

/** Writes the rendered card to the cache and opens the share sheet. */
internal fun shareRecapImage(context: Context, bitmap: Bitmap) {
    val dir = File(context.cacheDir, "recap").apply { mkdirs() }
    val file = File(dir, "playlog-recap.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PngQuality, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".recap.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** Deep, saturated pairs; white type sits on all of them. */
private val PageGradients = listOf(
    listOf(Color(0xFF3B1F7A), Color(0xFF8A2A6B)),
    listOf(Color(0xFF16351F), Color(0xFF3F5E12)),
    listOf(Color(0xFF0F2A4A), Color(0xFF3B1F7A)),
    listOf(Color(0xFF4A1A2E), Color(0xFF7A3A12)),
    listOf(Color(0xFF0E3B3B), Color(0xFF0F2A4A)),
    listOf(Color(0xFF3A2A0E), Color(0xFF6B1F4A)),
    listOf(Color(0xFF2A1A4A), Color(0xFF0E3B3B)),
)

private const val BackZone = 0.3f
private const val TrackAlpha = 0.3f
private const val SegmentMillis = 250
private const val PngQuality = 100
private val SegmentHeight = 3.dp
private val SegmentShape = RoundedCornerShape(SegmentHeight)
private val CloseSize = 26.dp
