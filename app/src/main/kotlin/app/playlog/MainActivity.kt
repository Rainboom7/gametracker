package app.playlog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.data.AccountRepository
import app.playlog.core.data.UserSettingsRepository
import app.playlog.core.designsystem.theme.PlaylogTheme
import app.playlog.ui.PlaylogApp
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Where a notification (or shortcut) asked to land; consumed by [PlaylogApp]. */
    private val pendingOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingOpen.value = intent?.getStringExtra(EXTRA_OPEN)
        setContent {
            PlaylogTheme {
                val open by pendingOpen.collectAsState()
                PlaylogApp(open = open, onOpened = { pendingOpen.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { pendingOpen.value = it }
    }

    companion object {
        /** A screen to open on launch, see `app.playlog.sync.MainActivityTargets`. */
        const val EXTRA_OPEN = "playlog.open"
    }
}

enum class StartDestination { LOADING, ONBOARDING, MAIN }

/** Decides once whether to open onboarding; later changes do not yank the user around. */
@HiltViewModel
class MainViewModel @Inject constructor(
    settings: UserSettingsRepository,
    accounts: AccountRepository,
) : ViewModel() {
    val start: StateFlow<StartDestination> = flow {
        val decided = combine(settings.settings, accounts.observeAccounts()) { s, a ->
            if (s.onboardingFinished || a.isNotEmpty()) StartDestination.MAIN else StartDestination.ONBOARDING
        }.first()
        emit(decided)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StartDestination.LOADING)
}
