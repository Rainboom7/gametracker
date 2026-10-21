package app.playlog.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.theme.Spacing

/**
 * Card tinted with a diagonal wash of [accents] over the surface, edged in the first accent.
 * The tint stays low so body text keeps its contrast on the dark surface.
 */
@Composable
fun GradientCard(
    accents: List<Color>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val wash = Brush.linearGradient(accents.map { it.copy(alpha = WashAlpha) })
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .background(wash)
            .border(1.dp, accents.first().copy(alpha = EdgeAlpha), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(Spacing.md),
        content = content,
    )
}

/** Small colored stat: icon in a tinted circle, a big value and a one-line label. */
@Composable
fun StatBubble(icon: ImageVector, value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(accent.copy(alpha = BubbleAlpha))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(18.dp))
        }
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val WashAlpha = 0.22f
private const val EdgeAlpha = 0.45f
private const val BubbleAlpha = 0.14f
