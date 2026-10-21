package app.playlog.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.data.GameNotesRepository
import app.playlog.core.model.GameNote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class GameNotesViewModel @Inject constructor(private val repository: GameNotesRepository) : ViewModel() {
    private val key = MutableStateFlow<String?>(null)

    val note: StateFlow<GameNote?> = key.filterNotNull()
        .flatMapLatest { repository.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    /** What the text field shows; saved after a pause in typing rather than on every key press. */
    val draft = MutableStateFlow<String?>(null)

    init {
        draft.filterNotNull().debounce(SAVE_DELAY_MS).onEach { save(it) }.launchIn(viewModelScope)
    }

    fun bind(entryKey: String) {
        key.value = entryKey
    }

    fun setRating(value: Int?) {
        val k = key.value ?: return
        viewModelScope.launch { repository.setRating(k, value) }
    }

    fun editText(value: String) {
        draft.value = value
    }

    /** Flushes a pending edit when the field loses focus or the card leaves the screen. */
    fun commitText() {
        draft.value?.let(::save)
    }

    private fun save(text: String) {
        val k = key.value ?: return
        if (text.trim() == note.value?.text.orEmpty()) return
        viewModelScope.launch { repository.setText(k, text) }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val SAVE_DELAY_MS = 600L
    }
}
