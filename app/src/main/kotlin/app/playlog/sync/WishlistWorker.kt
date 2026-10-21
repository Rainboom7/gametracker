package app.playlog.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.playlog.R
import app.playlog.core.data.WishlistRepository
import app.playlog.core.model.PriceDrop
import app.playlog.core.ui.platformNameRes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Re-prices the wishlist twice a day and posts one notification per game that went on sale. */
@HiltWorker
class WishlistWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val wishlist: WishlistRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val drops = wishlist.checkPrices()
        Log.i(TAG, "Wishlist check: ${drops.size} new sales")
        if (drops.isNotEmpty()) notify(drops)
        return Result.success()
    }

    private fun notify(drops: List<PriceDrop>) {
        val context = applicationContext
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        drops.forEach { drop ->
            val item = drop.item
            val store = context.getString(platformNameRes(item.platform))
            val text = drop.price.originalPriceText?.let { context.getString(R.string.deals_drop_text_was, drop.price.priceText, it, store) }
                ?: context.getString(R.string.deals_drop_text, drop.price.priceText, store)
            val open = PendingIntent.getActivity(
                context,
                item.key.hashCode(),
                Intent(Intent.ACTION_VIEW, Uri.parse(item.storeUrl)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_deal)
                .setContentTitle(context.getString(R.string.deals_drop_title, item.title, drop.price.discountPercent))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .build()
            @Suppress("MissingPermission") // checked above
            manager.notify(item.key.hashCode(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.deals_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "Playlog/deals"
        private const val CHANNEL = "wishlist-deals"
        private const val GROUP = "wishlist-deals"
        private const val UNIQUE = "wishlist-prices"
        private const val INTERVAL_HOURS = 12L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WishlistWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
