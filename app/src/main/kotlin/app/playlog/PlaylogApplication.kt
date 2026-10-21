package app.playlog

import android.app.Application
import android.util.Log
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.playlog.core.data.SyncScheduler
import app.playlog.core.data.UserSettingsRepository
import app.playlog.sync.WishlistWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PlaylogApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var settings: UserSettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            syncScheduler.setPeriodicSync(settings.settings.first().backgroundSync)
        }
        WishlistWorker.schedule(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .apply { if (BuildConfig.DEBUG) eventListener(ImageErrorLogger) }
        .build()
}

/** Debug only: say which image failed and why (the query string holds signed tokens, so it is cut). */
private object ImageErrorLogger : EventListener() {
    override fun onError(request: ImageRequest, result: ErrorResult) {
        Log.w("Playlog/image", "Failed ${request.data.toString().substringBefore('?')}", result.throwable)
    }
}
