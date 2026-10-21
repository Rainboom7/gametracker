package app.playlog.feature.profile

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.AccountRepository
import app.playlog.core.data.SyncScheduler
import app.playlog.core.model.Account
import app.playlog.core.model.Platform
import app.playlog.platform.api.LinkFieldType
import app.playlog.platform.api.LinkFlow
import app.playlog.platform.api.PlatformException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinkUiState(
    val platform: Platform,
    val flow: LinkFlow? = null,
    val input: Map<LinkFieldType, String> = emptyMap(),
    val isSubmitting: Boolean = false,
    val error: LinkError? = null,
    val linked: Account? = null,
)

sealed interface LinkError {
    data class Field(val field: LinkFieldType, val message: String) : LinkError
    data class General(val message: String) : LinkError
}

@HiltViewModel
class LinkAccountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accounts: AccountRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    private val platform = Platform.valueOf(savedStateHandle.toRoute<LinkAccountDestination>().platform)
    private val state = MutableStateFlow(LinkUiState(platform))
    val uiState: StateFlow<LinkUiState> = state.asStateFlow()

    init {
        restart()
    }

    /** Starts a fresh link flow; [keepError] leaves the previous failure visible after an automatic restart. */
    fun restart(keepError: Boolean = false) {
        if (!keepError) state.update { it.copy(error = null) }
        viewModelScope.launch {
            runSuspendCatching { accounts.startLink(platform) }
                .onSuccess { flow -> state.update { it.copy(flow = flow) } }
                .onFailure { e ->
                    Log.w(TAG, "startLink($platform) failed", e)
                    state.update { it.copy(error = LinkError.General(e.message ?: e.javaClass.simpleName)) }
                }
        }
    }

    fun onInput(field: LinkFieldType, value: String) = state.update { it.copy(input = it.input + (field to value), error = null) }

    /** WebView saw the redirect: complete immediately with the captured URL. */
    fun onCaptured(field: LinkFieldType, url: String) = submit(mapOf(field to url))

    fun submit() = submit(state.value.input.filterValues { it.isNotBlank() })

    private fun submit(input: Map<LinkFieldType, String>) {
        val flow = state.value.flow ?: return
        if (state.value.isSubmitting) return
        state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runSuspendCatching { accounts.completeLink(platform, flow, input) }
                .onSuccess { account ->
                    Log.i(TAG, "Linked $platform as ${account.displayName}")
                    state.update { it.copy(isSubmitting = false, linked = account) }
                    syncScheduler.requestSync(platform)
                }
                .onFailure { e ->
                    Log.w(TAG, "completeLink($platform) failed", e)
                    val error = when (e) {
                        is PlatformException.InvalidInput -> e.field?.let { LinkError.Field(it, e.message.orEmpty()) }
                            ?: LinkError.General(e.message.orEmpty())
                        else -> LinkError.General(e.message ?: e.javaClass.simpleName)
                    }
                    state.update { it.copy(isSubmitting = false, error = error) }
                    // PKCE / OAuth state is single-use: start a fresh flow for the next attempt.
                    if (e !is PlatformException.InvalidInput || e.field != LinkFieldType.STEAM_API_KEY) restart(keepError = true)
                }
        }
    }
}

private const val TAG = "Playlog"
