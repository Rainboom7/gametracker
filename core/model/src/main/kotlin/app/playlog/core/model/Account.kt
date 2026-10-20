package app.playlog.core.model

import java.time.Instant

data class Account(
    val platform: Platform,
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val lastSyncedAt: Instant? = null,
    val syncState: SyncState = SyncState.Idle,
)

sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Failed(val reason: SyncFailure, val message: String? = null) : SyncState
}

enum class SyncFailure { AUTH_EXPIRED, NETWORK, RATE_LIMITED, PRIVATE_PROFILE, UNKNOWN }
