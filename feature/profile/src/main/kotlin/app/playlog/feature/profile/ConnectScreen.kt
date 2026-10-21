package app.playlog.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.PlatformMonogram
import app.playlog.core.designsystem.component.PrimaryButton
import app.playlog.core.designsystem.component.SecondaryButton
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.model.Account
import app.playlog.core.model.Platform
import app.playlog.core.ui.accountStatusText
import app.playlog.core.ui.platformName

@Composable
fun ConnectRoute(onLink: (Platform) -> Unit, onFinished: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectScreen(
        accounts = state.accounts,
        onLink = onLink,
        onFinish = {
            viewModel.finishOnboarding()
            onFinished()
        },
    )
}

@Composable
fun ConnectScreen(accounts: List<Pair<Platform, Account?>>, onLink: (Platform) -> Unit, onFinish: () -> Unit) {
    val connected = accounts.count { it.second != null }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        LinearProgressIndicator(
            progress = { if (accounts.isEmpty()) 0f else connected / accounts.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.connect_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        accounts.forEach { (platform, account) -> PlatformCard(platform, account, onLink = { onLink(platform) }) }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(stringResource(R.string.connect_continue), onClick = onFinish, enabled = connected > 0)
        TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.connect_later))
        }
    }
}

@Composable
private fun PlatformCard(platform: Platform, account: Account?, onLink: () -> Unit) {
    val done = account != null
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (done) ConnectedBorder else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformMonogram(platform)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(platformName(platform), style = MaterialTheme.typography.titleMedium)
                    Text(accountStatusText(account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (done) {
                    Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            stringResource(R.string.connect_done),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (!done) {
                Text(howItConnects(platform), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton(stringResource(R.string.connect_button, platformName(platform)), onClick = onLink)
            }
        }
    }
}

@Composable
private fun howItConnects(platform: Platform): String = stringResource(
    when (platform) {
        Platform.STEAM -> R.string.connect_how_STEAM
        Platform.PLAYSTATION -> R.string.connect_how_PLAYSTATION
        Platform.NINTENDO -> R.string.connect_how_NINTENDO
    },
)

private val ConnectedBorder = Color(0xFF3A4A22)
