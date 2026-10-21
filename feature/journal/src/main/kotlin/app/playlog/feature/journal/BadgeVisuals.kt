package app.playlog.feature.journal

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import app.playlog.core.model.MilestoneKind
import app.playlog.core.ui.formatPlaytime
import java.time.Duration

@StringRes
fun badgeTitle(kind: MilestoneKind): Int = when (kind) {
    MilestoneKind.MARATHON -> R.string.badge_MARATHON
    MilestoneKind.VETERAN -> R.string.badge_VETERAN
    MilestoneKind.COLLECTOR -> R.string.badge_COLLECTOR
    MilestoneKind.FINISHER -> R.string.badge_FINISHER
    MilestoneKind.PERFECTIONIST -> R.string.badge_PERFECTIONIST
    MilestoneKind.MULTIPLATFORM -> R.string.badge_MULTIPLATFORM
    MilestoneKind.STREAK -> R.string.badge_STREAK
    MilestoneKind.BIG_DAY -> R.string.badge_BIG_DAY
    MilestoneKind.LOYAL -> R.string.badge_LOYAL
    MilestoneKind.PHOTOGRAPHER -> R.string.badge_PHOTOGRAPHER
    MilestoneKind.CRITIC -> R.string.badge_CRITIC
}

@StringRes
fun badgeDescription(kind: MilestoneKind): Int = when (kind) {
    MilestoneKind.MARATHON -> R.string.badge_desc_MARATHON
    MilestoneKind.VETERAN -> R.string.badge_desc_VETERAN
    MilestoneKind.COLLECTOR -> R.string.badge_desc_COLLECTOR
    MilestoneKind.FINISHER -> R.string.badge_desc_FINISHER
    MilestoneKind.PERFECTIONIST -> R.string.badge_desc_PERFECTIONIST
    MilestoneKind.MULTIPLATFORM -> R.string.badge_desc_MULTIPLATFORM
    MilestoneKind.STREAK -> R.string.badge_desc_STREAK
    MilestoneKind.BIG_DAY -> R.string.badge_desc_BIG_DAY
    MilestoneKind.LOYAL -> R.string.badge_desc_LOYAL
    MilestoneKind.PHOTOGRAPHER -> R.string.badge_desc_PHOTOGRAPHER
    MilestoneKind.CRITIC -> R.string.badge_desc_CRITIC
}

fun badgeIcon(kind: MilestoneKind): ImageVector = when (kind) {
    MilestoneKind.MARATHON -> Icons.Default.Timer
    MilestoneKind.VETERAN -> Icons.Default.MilitaryTech
    MilestoneKind.COLLECTOR -> Icons.Default.CollectionsBookmark
    MilestoneKind.FINISHER -> Icons.Default.Flag
    MilestoneKind.PERFECTIONIST -> Icons.Default.EmojiEvents
    MilestoneKind.MULTIPLATFORM -> Icons.Default.Devices
    MilestoneKind.STREAK -> Icons.Default.LocalFireDepartment
    MilestoneKind.BIG_DAY -> Icons.Default.WbSunny
    MilestoneKind.LOYAL -> Icons.Default.Favorite
    MilestoneKind.PHOTOGRAPHER -> Icons.Default.PhotoCamera
    MilestoneKind.CRITIC -> Icons.Default.RateReview
}

/** The badge's measure in its own unit: hours, days, years or a plain count. */
@Composable
fun badgeValue(kind: MilestoneKind, value: Long): String = when (kind) {
    MilestoneKind.MARATHON, MilestoneKind.VETERAN, MilestoneKind.BIG_DAY -> formatPlaytime(Duration.ofHours(value))
    MilestoneKind.STREAK -> pluralStringResource(R.plurals.badge_days, value.toInt(), value.toInt())
    MilestoneKind.LOYAL -> pluralStringResource(R.plurals.badge_years, value.toInt(), value.toInt())
    else -> value.toString()
}
