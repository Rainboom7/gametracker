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
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.playlog.MainActivity
import app.playlog.R

/** Channels and posting for background alerts; silently does nothing without the permission. */
internal class Notifier(private val context: Context) {
    enum class Channel(val id: String, @StringRes val title: Int) {
        DEALS("wishlist-deals", R.string.deals_channel_name),
        GIVEAWAYS("giveaways", R.string.giveaways_channel_name),
        DIGEST("weekly-digest", R.string.digest_channel_name),
        BADGES("badges", R.string.badges_channel_name),
    }

    val allowed: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(channel: Channel, id: Int, title: String, text: String, open: Intent) {
        if (!allowed) return
        ensure(channel)
        val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_deal)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setGroup(channel.id)
            .build()
        @Suppress("MissingPermission") // checked by `allowed`
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun browser(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    /** Opens the app on [destination] (see MainActivity.EXTRA_OPEN). */
    fun app(destination: String) = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(MainActivity.EXTRA_OPEN, destination)

    private fun ensure(channel: Channel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channel.id) == null) {
            manager.createNotificationChannel(NotificationChannel(channel.id, context.getString(channel.title), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
