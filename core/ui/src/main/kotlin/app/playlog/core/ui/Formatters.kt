package app.playlog.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import app.playlog.core.model.Platform
import app.playlog.platform.api.LinkFieldType
import app.playlog.platform.api.LinkStepType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
@ReadOnlyComposable
fun formatPlaytime(duration: Duration): String = when (val v = PlaytimeFormat.of(duration)) {
    is PlaytimeFormat.Value.Minutes -> stringResource(R.string.duration_minutes, v.minutes)
    is PlaytimeFormat.Value.Hours -> stringResource(R.string.duration_hours, v.text)
}

@Composable
@ReadOnlyComposable
fun formatRelativeDate(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (instant == null) return stringResource(R.string.date_never)
    val days = PlaytimeFormat.daysAgo(instant, LocalDate.now(zone), zone)
    return when {
        days <= 0 -> stringResource(R.string.date_today)
        days == 1L -> stringResource(R.string.date_yesterday)
        days < RelativeDaysLimit -> stringResource(R.string.date_days_ago, days.toInt())
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(LocalDate.ofInstant(instant, zone))
    }
}

@Composable
@ReadOnlyComposable
fun platformName(platform: Platform?): String = stringResource(platformNameRes(platform))

/** For non-Compose callers such as notifications. */
@StringRes
fun platformNameRes(platform: Platform?): Int = when (platform) {
    null -> R.string.platform_all
    Platform.STEAM -> R.string.platform_steam
    Platform.PLAYSTATION -> R.string.platform_playstation
    Platform.NINTENDO -> R.string.platform_nintendo
}

@Composable
@ReadOnlyComposable
fun linkStepText(type: LinkStepType): String = stringResource(
    when (type) {
        LinkStepType.STEAM_OPEN_API_KEY_PAGE -> R.string.link_step_STEAM_OPEN_API_KEY_PAGE
        LinkStepType.STEAM_MAKE_PROFILE_PUBLIC -> R.string.link_step_STEAM_MAKE_PROFILE_PUBLIC
        LinkStepType.PSN_SIGN_IN -> R.string.link_step_PSN_SIGN_IN
        LinkStepType.PSN_OPEN_NPSSO_PAGE -> R.string.link_step_PSN_OPEN_NPSSO_PAGE
        LinkStepType.NINTENDO_SIGN_IN -> R.string.link_step_NINTENDO_SIGN_IN
        LinkStepType.NINTENDO_COPY_LINK -> R.string.link_step_NINTENDO_COPY_LINK
    },
)

@Composable
@ReadOnlyComposable
fun linkFieldLabel(type: LinkFieldType): String = stringResource(
    when (type) {
        LinkFieldType.STEAM_API_KEY -> R.string.link_field_STEAM_API_KEY
        LinkFieldType.STEAM_PROFILE -> R.string.link_field_STEAM_PROFILE
        LinkFieldType.PSN_NPSSO -> R.string.link_field_PSN_NPSSO
        LinkFieldType.PSN_REDIRECT_URL -> R.string.link_field_PSN_REDIRECT_URL
        LinkFieldType.NINTENDO_REDIRECT_URL -> R.string.link_field_NINTENDO_REDIRECT_URL
    },
)

private const val RelativeDaysLimit = 30
