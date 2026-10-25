package app.playlog.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.Platform

private enum class GuideSection(@StringRes val title: Int) {
    BEFORE(R.string.link_guide_before),
    STEPS(R.string.link_guide_steps),
    PRIVACY(R.string.link_guide_privacy),
    ERRORS(R.string.link_guide_errors),
}

/** Long-form help for a platform's link screen; the step-by-step part starts expanded. */
@Composable
fun LinkGuide(platform: Platform, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.link_guide_title), style = MaterialTheme.typography.titleMedium)
        GuideSection.entries.forEach { section ->
            GuideCard(
                title = stringResource(section.title),
                body = stringResource(guideBody(platform, section)),
                initiallyExpanded = section == GuideSection.STEPS,
            )
        }
    }
}

@Composable
private fun GuideCard(title: String, body: String, initiallyExpanded: Boolean) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    body.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                        val isHeading = line.endsWith(':')
                        Text(
                            line,
                            style = if (isHeading) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                            color = if (isHeading) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun guideBody(platform: Platform, section: GuideSection): Int = when (platform) {
    Platform.STEAM -> when (section) {
        GuideSection.BEFORE -> R.string.link_guide_STEAM_before
        GuideSection.STEPS -> R.string.link_guide_STEAM_steps
        GuideSection.PRIVACY -> R.string.link_guide_STEAM_privacy
        GuideSection.ERRORS -> R.string.link_guide_STEAM_errors
    }
    Platform.PLAYSTATION -> when (section) {
        GuideSection.BEFORE -> R.string.link_guide_PLAYSTATION_before
        GuideSection.STEPS -> R.string.link_guide_PLAYSTATION_steps
        GuideSection.PRIVACY -> R.string.link_guide_PLAYSTATION_privacy
        GuideSection.ERRORS -> R.string.link_guide_PLAYSTATION_errors
    }
    Platform.NINTENDO -> when (section) {
        GuideSection.BEFORE -> R.string.link_guide_NINTENDO_before
        GuideSection.STEPS -> R.string.link_guide_NINTENDO_steps
        GuideSection.PRIVACY -> R.string.link_guide_NINTENDO_privacy
        GuideSection.ERRORS -> R.string.link_guide_NINTENDO_errors
    }
}
