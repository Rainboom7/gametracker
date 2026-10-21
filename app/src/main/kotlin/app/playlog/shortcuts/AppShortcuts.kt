package app.playlog.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.playlog.MainActivity
import app.playlog.R
import app.playlog.sync.MainActivityTargets

/**
 * Long-press launcher shortcuts. Published at runtime rather than from XML because a static
 * shortcut has to hard-code the package, and debug builds use a different one.
 */
object AppShortcuts {
    private data class Shortcut(val target: String, val short: Int, val long: Int, val icon: Int)

    private val Published = listOf(
        Shortcut(MainActivityTargets.ASK, R.string.shortcut_ask, R.string.shortcut_ask_long, R.drawable.ic_shortcut_ask),
        Shortcut(MainActivityTargets.DEALS, R.string.shortcut_deals, R.string.shortcut_deals_long, R.drawable.ic_shortcut_deals),
        Shortcut(MainActivityTargets.ACTIVITY, R.string.shortcut_activity, R.string.shortcut_activity_long, R.drawable.ic_shortcut_activity),
        Shortcut(MainActivityTargets.ROULETTE, R.string.shortcut_roulette, R.string.shortcut_roulette_long, R.drawable.ic_shortcut_roulette),
    )

    fun publish(context: Context) {
        val shortcuts = Published.map { s ->
            ShortcutInfoCompat.Builder(context, s.target)
                .setShortLabel(context.getString(s.short))
                .setLongLabel(context.getString(s.long))
                .setIcon(IconCompat.createWithResource(context, s.icon))
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_OPEN, s.target))
                .build()
        }
        // Some launchers refuse shortcuts (or throttle updates); the app works the same without them.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }
}
