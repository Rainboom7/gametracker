package app.playlog.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.playlog.core.designsystem.component.PlatformMonogram
import app.playlog.core.designsystem.component.PrimaryButton
import app.playlog.core.designsystem.component.SecondaryButton
import app.playlog.core.designsystem.theme.Spacing
import app.playlog.core.ui.linkFieldLabel
import app.playlog.core.ui.linkStepText
import app.playlog.core.ui.platformName
import app.playlog.platform.api.LinkFieldType
import app.playlog.platform.api.LinkStep

@Composable
fun LinkAccountRoute(onDone: () -> Unit, viewModel: LinkAccountViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.linked) {
        if (state.linked != null) onDone()
    }
    LinkAccountScreen(
        state = state,
        onBack = onDone,
        onInput = viewModel::onInput,
        onCaptured = viewModel::onCaptured,
        onSubmit = viewModel::submit,
        onRetry = { viewModel.restart() },
    )
}

@Composable
fun LinkAccountScreen(
    state: LinkUiState,
    onBack: () -> Unit,
    onInput: (LinkFieldType, String) -> Unit,
    onCaptured: (LinkFieldType, String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val context = LocalContext.current
    var webStep by remember { mutableStateOf<LinkStep?>(null) }
    val flow = state.flow

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            PlatformMonogram(state.platform)
            Spacer(Modifier.padding(start = Spacing.sm))
            Text(stringResource(R.string.link_title, platformName(state.platform)), style = MaterialTheme.typography.headlineMedium)
        }
        if (flow == null && state.error != null) {
            ErrorText(state.error)
            SecondaryButton(stringResource(R.string.link_retry), onClick = onRetry)
        } else if (flow == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            flow.steps.forEachIndexed { i, step ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("${i + 1}. ${linkStepText(step.type)}", style = MaterialTheme.typography.bodyLarge)
                    val url = step.url
                    when {
                        url != null && step.captureRedirectPrefix != null ->
                            PrimaryButton(stringResource(app.playlog.core.ui.R.string.link_step_sign_in), onClick = { webStep = step }, loading = state.isSubmitting)
                        url != null ->
                            SecondaryButton(
                                stringResource(app.playlog.core.ui.R.string.link_step_open),
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                                icon = Icons.AutoMirrored.Filled.OpenInNew,
                            )
                    }
                }
            }
            if (flow.fields.isNotEmpty()) {
                if (flow.steps.any { it.captureRedirectPrefix != null }) {
                    Text(stringResource(R.string.link_or_manual), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                flow.fields.forEach { field -> LinkField(field, state, onInput) }
                PrimaryButton(
                    stringResource(R.string.link_submit),
                    onClick = onSubmit,
                    loading = state.isSubmitting,
                    enabled = flow.fields.all { !state.input[it].isNullOrBlank() },
                )
            }
            (state.error as? LinkError.General)?.let { ErrorText(it) }
        }
    }

    webStep?.let { step ->
        Dialog(onDismissRequest = { webStep = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { webStep = null }) { Icon(Icons.Default.Close, contentDescription = null) }
                    Text(stringResource(R.string.link_webview_title), style = MaterialTheme.typography.titleLarge)
                }
                CaptureWebView(
                    url = step.url!!,
                    capturePrefix = step.captureRedirectPrefix!!,
                    onCaptured = { captured ->
                        webStep = null
                        step.captureField?.let { onCaptured(it, captured) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ErrorText(error: LinkError) {
    val message = when (error) {
        is LinkError.General -> error.message
        is LinkError.Field -> error.message
    }
    Text(stringResource(R.string.link_error_generic, message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun LinkField(field: LinkFieldType, state: LinkUiState, onInput: (LinkFieldType, String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val error = (state.error as? LinkError.Field)?.takeIf { it.field == field }
    OutlinedTextField(
        value = state.input[field].orEmpty(),
        onValueChange = { onInput(field, it) },
        label = { Text(linkFieldLabel(field)) },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error.message) }
        } else {
            null
        },
        visualTransformation = if (field.isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (field.isSecret) KeyboardType.Password else KeyboardType.Uri, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { clipboard.getText()?.text?.let { onInput(field, it.trim()) } }) {
                Icon(Icons.Default.ContentPaste, contentDescription = stringResource(R.string.link_paste))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
