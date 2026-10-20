package app.playlog.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.playlog.core.data.SyncEngine
import app.playlog.core.model.Platform
import app.playlog.platform.api.PlatformException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs [SyncEngine] for one platform (input [KEY_PLATFORM]) or all of them. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val platform = inputData.getString(KEY_PLATFORM)?.let(Platform::valueOf)
        val results = if (platform != null) listOfNotNull(engine.sync(platform)) else engine.syncAll().results
        // Network hiccups and rate limits are worth a retry with backoff; auth problems are not.
        val retryable = results.any { it.error is PlatformException.Network || it.error is PlatformException.RateLimited }
        if (retryable && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        return Result.success(workDataOf(KEY_FAILED to results.filterNot { it.isSuccess }.map { it.platform.name }.toTypedArray()))
    }

    companion object {
        const val KEY_PLATFORM = "platform"
        const val KEY_FAILED = "failed"
        private const val MAX_ATTEMPTS = 3
    }
}
