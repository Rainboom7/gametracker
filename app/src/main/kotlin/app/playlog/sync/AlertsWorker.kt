package app.playlog.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.playlog.R
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.GiveawaysRepository
import app.playlog.core.data.UserSettingsRepository
import app.playlog.core.data.WishlistRepository
import app.playlog.core.ui.platformNameRes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Every few hours: wishlist sales and new giveaways. Each step is independent, so one store
 * being down never hides the others' alerts.
 */
@HiltWorker
class AlertsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: UserSettingsRepository,
    private val wishlist: WishlistRepository,
    private val giveaways: GiveawaysRepository,
) : CoroutineWorker(context, params) {
    private val notifier = Notifier(context)

    override suspend fun doWork(): Result {
        val prefs = settings.settings.first()
        if (prefs.dealAlerts) {
            step("wishlist") { wishlistSales() }
            step("giveaways") { newGiveaways() }
        }
        return Result.success()
    }

    private suspend fun step(name: String, block: suspend () -> Unit) {
        runSuspendCatching { block() }.onFailure { Log.w(TAG, "Alerts: $name failed", it) }
    }

    private suspend fun wishlistSales() {
        val drops = wishlist.checkPrices()
        Log.i(TAG, "Wishlist check: ${drops.size} new sales")
        drops.forEach { drop ->
            val item = drop.item
            val store = applicationContext.getString(platformNameRes(item.platform))
            val text = drop.price.originalPriceText?.let { applicationContext.getString(R.string.deals_drop_text_was, drop.price.priceText, it, store) }
                ?: applicationContext.getString(R.string.deals_drop_text, drop.price.priceText, store)
            notifier.post(
                Notifier.Channel.DEALS,
                item.key.hashCode(),
                applicationContext.getString(R.string.deals_drop_title, item.title, drop.price.discountPercent),
                text,
                notifier.browser(item.storeUrl),
            )
        }
    }

    private suspend fun newGiveaways() {
        val fresh = giveaways.takeNew()
        Log.i(TAG, "Giveaways: ${fresh.size} new")
        when {
            fresh.isEmpty() -> Unit
            fresh.size == 1 -> fresh.single().let { g ->
                notifier.post(
                    Notifier.Channel.GIVEAWAYS,
                    g.id.hashCode(),
                    applicationContext.getString(R.string.giveaway_new_title, g.title),
                    applicationContext.getString(R.string.giveaway_new_text, g.platformsText.ifBlank { g.store.name }),
                    notifier.browser(g.url),
                )
            }
            else -> notifier.post(
                Notifier.Channel.GIVEAWAYS,
                GIVEAWAYS_SUMMARY_ID,
                applicationContext.resources.getQuantityString(R.plurals.giveaways_new_many_title, fresh.size, fresh.size),
                fresh.joinToString(", ") { it.title },
                notifier.app(MainActivityTargets.GIVEAWAYS),
            )
        }
    }

    companion object {
        private const val TAG = "Playlog/alerts"
        private const val UNIQUE = "alerts"
        private const val LEGACY_UNIQUE = "wishlist-prices"
        private const val INTERVAL_HOURS = 6L
        private const val GIVEAWAYS_SUMMARY_ID = 7_001

        fun schedule(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork(LEGACY_UNIQUE)
            val request = PeriodicWorkRequestBuilder<AlertsWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

/** Values of MainActivity.EXTRA_OPEN, shared by notifications and app shortcuts. */
object MainActivityTargets {
    const val ASK = "ask"
    const val DEALS = "deals"
    const val GIVEAWAYS = "giveaways"
    const val ACTIVITY = "activity"
    const val RECAP = "recap"
    const val ROULETTE = "roulette"
    const val BADGES = "badges"
}
