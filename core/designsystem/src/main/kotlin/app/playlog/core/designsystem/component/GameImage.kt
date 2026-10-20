package app.playlog.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Remote image that walks down [urls] when one fails (Steam library art 404s for some apps, then
 * `header.jpg` is used). Shows the title's initial on a neutral tile when nothing loads.
 */
@Composable
fun GameImage(
    urls: List<String?>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderText: String = "",
    contentScale: ContentScale = ContentScale.Crop,
) {
    val candidates = remember(urls) { urls.filterNotNull().distinct() }
    var index by remember(candidates) { mutableIntStateOf(0) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (index < candidates.size) {
            AsyncImage(
                model = candidates[index],
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onError = { index++ },
            )
        } else if (placeholderText.isNotEmpty()) {
            Text(
                placeholderText.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
