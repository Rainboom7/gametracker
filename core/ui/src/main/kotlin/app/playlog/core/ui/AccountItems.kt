package app.playlog.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.playlog.core.designsystem.component.PlatformDot
import app.playlog.core.designsystem.component.PlatformMonogram
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.core.model.Account
import app.playlog.core.model.Platform
import app.playlog.core.model.SyncFailure
import app.playlog.core.model.SyncState

@Composable
fun accountStatusText(account: Account?): String = when {
    account == null -> stringResource(R.string.account_not_connected)
    account.syncState is SyncState.Running -> stringResource(R.string.account_syncing)
    account.syncState is SyncState.Failed -> failedStatusText(account.syncState as SyncState.Failed)
    account.lastSyncedAt != null -> stringResource(R.string.account_synced_at, formatRelativeDate(account.lastSyncedAt))
    else -> stringResource(R.string.account_never_synced)
}

/** Network and unknown failures carry the underlying error, so a report from the device says what broke. */
@Composable
private fun failedStatusText(state: SyncState.Failed): String {
    val base = stringResource(
        when (state.reason) {
            SyncFailure.AUTH_EXPIRED -> R.string.account_auth_expired
            SyncFailure.NETWORK -> R.string.account_network_error
            SyncFailure.RATE_LIMITED -> R.string.account_rate_limited
            SyncFailure.PRIVATE_PROFILE -> R.string.account_private_profile
            SyncFailure.UNKNOWN -> R.string.account_unknown_error
        },
    )
    if (state.message?.contains(InterceptedMarker) == true) return stringResource(R.string.account_network_intercepted)
    val detail = state.message?.takeIf { state.reason == SyncFailure.NETWORK || state.reason == SyncFailure.UNKNOWN }
    return if (detail.isNullOrBlank()) base else "$base (${detail.take(MaxDetailLength)})"
}

private const val MaxDetailLength = 120

// Same text as AiaTrustManager.INTERCEPTED_MARKER; core:ui does not depend on core:network.
private const val InterceptedMarker = "TLS intercepted"

@Composable
fun AccountRow(platform: Platform, account: Account?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val failed = account?.syncState is SyncState.Failed
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformMonogram(platform)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account?.displayName ?: platformName(platform), style = MaterialTheme.typography.titleMedium)
            Text(
                accountStatusText(account),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) PlaylogColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (account != null) {
            PlatformDot(if (failed) PlaylogColors.Warning else MaterialTheme.colorScheme.primary)
        }
    }
}
