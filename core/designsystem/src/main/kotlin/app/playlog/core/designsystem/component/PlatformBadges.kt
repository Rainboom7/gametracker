package app.playlog.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.theme.monogram
import app.playlog.core.designsystem.theme.platformColors
import app.playlog.core.model.Platform

/** Small label on covers and lists. Color is never the only cue: it always carries the monogram. */
@Composable
fun PlatformBadge(platform: Platform, modifier: Modifier = Modifier, onImage: Boolean = false) {
    val colors = platformColors(platform)
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (onImage) BadgeOnImageBg else colors.badgeBg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = platform.displayName },
    ) {
        Text(platform.monogram, style = MaterialTheme.typography.labelSmall, color = colors.badgeText)
    }
}

@Composable
fun PlatformBadgeRow(platforms: Collection<Platform>, modifier: Modifier = Modifier, onImage: Boolean = false) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        platforms.sortedBy { it.ordinal }.forEach { PlatformBadge(it, onImage = onImage) }
    }
}

/** Square tile with the platform monogram, used on account cards. */
@Composable
fun PlatformMonogram(platform: Platform, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = platformColors(platform)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.badgeBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(platform.monogram, style = MaterialTheme.typography.titleLarge, color = colors.accent)
    }
}

@Composable
fun PlatformDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Single-select filter chip; [platform] null means "all". */
@Composable
fun PlatformFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    platform: Platform? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (platform != null) {
                    PlatformDot(platformColors(platform).accent)
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.background,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

private val BadgeOnImageBg = Color(0xD10D0E12)
