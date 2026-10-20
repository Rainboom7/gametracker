package app.playlog.core.testing

import app.playlog.core.common.AppClock
import app.playlog.core.data.SyncScheduler
import app.playlog.core.model.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FixedClock(var instant: Instant = Instant.parse("2025-10-20T12:00:00Z"), private val zone: ZoneId = ZoneOffset.UTC) : AppClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zone
}

/** Records sync requests instead of enqueueing WorkManager jobs. */
class FakeSyncScheduler : SyncScheduler {
    override val isSyncing = MutableStateFlow(false)
    val requests = mutableListOf<Platform?>()
    var periodicEnabled: Boolean? = null

    override fun requestSync(platform: Platform?) {
        requests += platform
    }

    override fun setPeriodicSync(enabled: Boolean) {
        periodicEnabled = enabled
    }
}
