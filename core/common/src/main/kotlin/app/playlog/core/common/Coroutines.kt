package app.playlog.core.common

import kotlinx.coroutines.CancellationException

/** Like [runCatching] but never swallows coroutine cancellation. */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
