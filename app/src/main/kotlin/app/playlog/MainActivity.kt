package app.playlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.playlog.core.data.AccountRepository
import app.playlog.core.data.UserSettingsRepository
import app.playlog.core.designsystem.theme.PlaylogTheme
import app.playlog.ui.PlaylogApp
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PlaylogTheme {
                PlaylogApp()
            }
        }
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
