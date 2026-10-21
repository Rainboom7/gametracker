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
import app.playlog.core.common.AppClock
import app.playlog.core.data.ActivityRepository
import app.playlog.core.data.BadgesRepository
import app.playlog.core.data.GiveawaysRepository
import app.playlog.core.data.WeeklyDigest
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.ui.formatPlaytime
import app.playlog.feature.journal.badgeDescription
import app.playlog.feature.journal.badgeTitle
import app.playlog.core.data.UserSettingsRepository
import app.playlog.core.data.WishlistRepository
import app.playlog.core.ui.platformNameRes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Every few hours: wishlist sales, new giveaways, badge level-ups and the Sunday digest. Each step
 * is independent, so one store being down never hides the others' alerts.
 */
@HiltWorker
class AlertsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: UserSettingsRepository,
    private val wishlist: WishlistRepository,
    private val giveaways: GiveawaysRepository,
    private val badges: BadgesRepository,
    private val activity: ActivityRepository,
    private val clock: AppClock,
) : CoroutineWorker(context, params) {
    private val notifier = Notifier(context)
    private val markers = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val prefs = settings.settings.first()
        if (prefs.dealAlerts) {
            step("wishlist") { wishlistSales() }
            step("giveaways") { newGiveaways() }
        }
        // Always record level-ups, so turning alerts on later does not replay old badges.
        step("badges") { badgeUnlocks(notify = prefs.badgeAlerts) }
        if (prefs.weeklySummary) step("digest") { weeklyDigest() }
        return Result.success()
    }

    private suspend fun step(name: String, block: suspend () -> Unit) {
        runSuspendCatching { block() }.onFailure { Log.w(TAG, "Alerts: $name failed", it) }
    }

    private suspend fun wishlistSales() {
        val check = wishlist.check()
        val drops = check.drops
        Log.i(TAG, "Wishlist check: ${drops.size} new sales, ${check.released.size} released")
        check.released.forEach { item ->
            notifier.post(
                Notifier.Channel.DEALS,
                "release:${item.key}".hashCode(),
                applicationContext.getString(R.string.wishlist_released_title, item.title),
                item.price?.let { applicationContext.getString(R.string.wishlist_released_price, it.priceText) }
                    ?: applicationContext.getString(R.string.wishlist_released_text),
                notifier.browser(item.storeUrl),
            )
        }
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

    private suspend fun badgeUnlocks(notify: Boolean) {
        val unlocked = badges.takeUnlocks()
        Log.i(TAG, "Badges: ${unlocked.size} level-ups")
        if (!notify || unlocked.isEmpty()) return
        val context = applicationContext
        val (title, text) = if (unlocked.size == 1) {
            val badge = unlocked.single()
            context.getString(R.string.badge_unlocked_title, context.getString(badgeTitle(badge.kind)), badge.level) to
                context.getString(R.string.badge_unlocked_text, context.getString(badgeDescription(badge.kind)))
        } else {
            context.resources.getQuantityString(R.plurals.badges_unlocked_title, unlocked.size, unlocked.size) to
                unlocked.joinToString(", ") { context.getString(badgeTitle(it.kind)) }
        }
        notifier.post(Notifier.Channel.BADGES, BADGES_ID, title, text, notifier.app(MainActivityTargets.BADGES))
    }

    private suspend fun weeklyDigest() {
        val now = clock.now().atZone(clock.zone())
        val last = markers.getLong(DIGEST_WEEK, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
        val week = WeeklyDigest.dueWeek(now, last) ?: return
        val summary = activity.observeSummary(ActivityPeriod.WEEK, week).first()
        markers.edit().putLong(DIGEST_WEEK, week.toEpochDay()).apply()
        if (summary.total.isZero) return
        val context = applicationContext
        val lines = buildList {
            summary.topGames.firstOrNull()?.let { add(context.getString(R.string.digest_top, it.entry.title, context.formatPlaytime(it.playtime))) }
            val delta = summary.total - summary.previousTotal
            when {
                summary.previousTotal.isZero -> Unit
                delta.toMinutes() >= DIGEST_MIN_DELTA_MINUTES -> add(context.getString(R.string.digest_more, context.formatPlaytime(delta)))
                delta.negated().toMinutes() >= DIGEST_MIN_DELTA_MINUTES -> add(context.getString(R.string.digest_less, context.formatPlaytime(delta.negated())))
            }
            add(context.getString(R.string.digest_cta))
        }
        notifier.post(
            Notifier.Channel.DIGEST,
            DIGEST_ID,
            context.getString(R.string.digest_title, context.formatPlaytime(summary.total)),
            lines.joinToString(" "),
            notifier.app(MainActivityTargets.RECAP),
        )
    }

    companion object {
        private const val TAG = "Playlog/alerts"
        private const val UNIQUE = "alerts"
        private const val LEGACY_UNIQUE = "wishlist-prices"
        private const val INTERVAL_HOURS = 6L
        private const val GIVEAWAYS_SUMMARY_ID = 7_001
        private const val BADGES_ID = 7_002
        private const val DIGEST_ID = 7_003
        private const val PREFS = "alerts"
        private const val DIGEST_WEEK = "digest_week"
        private const val DIGEST_MIN_DELTA_MINUTES = 30L

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
