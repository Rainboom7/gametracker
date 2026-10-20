package app.playlog.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.playlog.core.data.SyncScheduler
import app.playlog.core.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class WorkManagerSyncScheduler(context: Context) : SyncScheduler {
    private val workManager = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override val isSyncing: Flow<Boolean> = workManager.getWorkInfosByTagFlow(TAG)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
        .distinctUntilChanged()

    override fun requestSync(platform: Platform?) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_PLATFORM to platform?.name))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork("sync-${platform?.name ?: "all"}", ExistingWorkPolicy.KEEP, request)
    }

    override fun setPeriodicSync(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val TAG = "sync"
        const val PERIODIC = "sync-periodic"
    }
}
