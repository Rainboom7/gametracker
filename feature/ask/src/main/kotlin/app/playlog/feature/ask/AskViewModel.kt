package app.playlog.feature.ask

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.AskEngine
import app.playlog.core.model.AskAnswer
import app.playlog.core.model.DealPick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AskResult {
    /** Nothing asked yet: the screen shows examples. */
    data object Idle : AskResult
    data object Thinking : AskResult
    data class Answer(val answer: AskAnswer) : AskResult
    data class Failed(val message: String?) : AskResult
}

data class AskUiState(val query: String = "", val result: AskResult = AskResult.Idle, val openDeal: DealPick? = null)

@HiltViewModel
class AskViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: AskEngine,
) : ViewModel() {
    private val state = MutableStateFlow(AskUiState())
    val uiState: StateFlow<AskUiState> = state.asStateFlow()
    private var job: Job? = null

    init {
        savedStateHandle.toRoute<AskDestination>().query?.takeIf { it.isNotBlank() }?.let(::ask)
    }

    fun onQueryChange(query: String) = state.update { it.copy(query = query) }

    fun ask(query: String = state.value.query) {
        val text = query.trim()
        if (text.isEmpty()) return
        job?.cancel()
        state.update { it.copy(query = text, result = AskResult.Thinking) }
        job = viewModelScope.launch {
            val result = runSuspendCatching { engine.ask(text) }
                .onFailure { Log.w(TAG, "Ask failed: ${it.javaClass.simpleName}: ${it.message}", it) }
                .fold({ AskResult.Answer(it) }, { AskResult.Failed("${it.javaClass.simpleName}: ${it.message}") })
            state.update { it.copy(result = result) }
        }
    }

    fun clear() {
        job?.cancel()
        state.value = AskUiState()
    }

    fun openDeal(pick: DealPick?) = state.update { it.copy(openDeal = pick) }
}

private const val TAG = "Playlog/ask"
