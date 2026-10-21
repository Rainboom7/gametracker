package app.playlog.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.SecondaryButton
import app.playlog.core.designsystem.component.SectionHeader
import app.playlog.core.designsystem.component.SettingSwitchRow
import app.playlog.core.designsystem.component.StatTile
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.Account
import app.playlog.core.model.Platform
import app.playlog.core.model.SyncFailure
import app.playlog.core.model.SyncState
import app.playlog.core.ui.AccountRow
import app.playlog.core.ui.platformName

@Composable
fun ProfileRoute(onLink: (Platform) -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        onLink = onLink,
        onRefresh = { viewModel.refresh() },
        onSyncPlatform = { viewModel.refresh(it) },
        onUnlink = viewModel::unlink,
        onBackgroundSync = viewModel::setBackgroundSync,
        onWifiOnlyMedia = viewModel::setWifiOnlyMedia,
        onWeeklySummary = viewModel::setWeeklySummary,
        onDealAlerts = viewModel::setDealAlerts,
        onBadgeAlerts = viewModel::setBadgeAlerts,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onLink: (Platform) -> Unit,
    onRefresh: () -> Unit,
    onSyncPlatform: (Platform) -> Unit,
    onUnlink: (Platform, Boolean) -> Unit,
    onBackgroundSync: (Boolean) -> Unit,
    onWifiOnlyMedia: (Boolean) -> Unit,
    onWeeklySummary: (Boolean) -> Unit,
    onDealAlerts: (Boolean) -> Unit = {},
    onBadgeAlerts: (Boolean) -> Unit = {},
) {
    var unlinkTarget by remember { mutableStateOf<Platform?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(state.stats.gameCount.toString(), stringResource(R.string.profile_games), Modifier.weight(1f))
            StatTile(
                (state.stats.totalPlaytime.toHours()).toString(),
                stringResource(R.string.profile_hours),
                Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.primary,
            )
            StatTile(state.stats.completedCount.toString(), stringResource(R.string.profile_completed), Modifier.weight(1f))
        }
        Column {
            SectionHeader(stringResource(R.string.profile_accounts)) {
                SecondaryButton(
                    text = stringResource(if (state.isSyncing) R.string.profile_refreshing else R.string.profile_refresh),
                    onClick = onRefresh,
                    enabled = !state.isSyncing && state.accounts.any { it.second != null },
                    icon = Icons.Default.Refresh,
                )
            }
            state.accounts.forEach { (platform, account) ->
                AccountEntry(
                    platform = platform,
                    account = account,
                    onLink = { onLink(platform) },
                    onSync = { onSyncPlatform(platform) },
                    onUnlink = { unlinkTarget = platform },
                )
            }
        }
        Column {
            SectionHeader(stringResource(R.string.profile_settings))
            SettingSwitchRow(
                stringResource(R.string.profile_background_sync),
                state.settings.backgroundSync,
                onBackgroundSync,
                subtitle = stringResource(R.string.profile_background_sync_hint),
            )
            SettingSwitchRow(stringResource(R.string.profile_wifi_media), state.settings.wifiOnlyMedia, onWifiOnlyMedia)
            SettingSwitchRow(
                stringResource(R.string.profile_weekly_summary),
                state.settings.weeklySummary,
                onWeeklySummary,
                subtitle = stringResource(R.string.profile_weekly_summary_hint),
            )
            SettingSwitchRow(
                stringResource(R.string.profile_deal_alerts),
                state.settings.dealAlerts,
                onDealAlerts,
                subtitle = stringResource(R.string.profile_deal_alerts_hint),
            )
            SettingSwitchRow(stringResource(R.string.profile_badge_alerts), state.settings.badgeAlerts, onBadgeAlerts)
        }
        Spacer(Modifier.height(Spacing.lg))
    }

    unlinkTarget?.let { platform ->
        AlertDialog(
            onDismissRequest = { unlinkTarget = null },
            title = { Text(stringResource(R.string.profile_unlink_title, platformName(platform))) },
            text = { Text(stringResource(R.string.profile_unlink_body)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        onUnlink(platform, false)
                        unlinkTarget = null
                    }) { Text(stringResource(R.string.profile_unlink_keep)) }
                    TextButton(onClick = {
                        onUnlink(platform, true)
                        unlinkTarget = null
                    }) { Text(stringResource(R.string.profile_unlink_delete), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { unlinkTarget = null }) { Text(stringResource(R.string.profile_cancel)) } },
        )
    }
}

/** Tap: link when disconnected, relink when the token expired, otherwise a menu. */
@Composable
private fun AccountEntry(platform: Platform, account: Account?, onLink: () -> Unit, onSync: () -> Unit, onUnlink: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val expired = (account?.syncState as? SyncState.Failed)?.reason == SyncFailure.AUTH_EXPIRED
    Column {
        AccountRow(platform, account, onClick = {
            when {
                account == null || expired -> onLink()
                else -> menu = true
            }
        })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.profile_sync_now)) }, onClick = {
                menu = false
                onSync()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.profile_relink)) }, onClick = {
                menu = false
                onLink()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.profile_unlink)) }, onClick = {
                menu = false
                onUnlink()
            })
        }
    }
}

