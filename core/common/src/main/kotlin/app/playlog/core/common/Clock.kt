package app.playlog.core.common

import java.time.Instant
import java.time.ZoneId

/** Time source; tests use a fixed implementation. */
interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId
}

object SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
