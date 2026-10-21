package app.playlog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.playlog.MainActivity
import app.playlog.R
import app.playlog.core.common.AppClock
import app.playlog.core.data.ActivityRepository
import app.playlog.core.data.LibraryRepository
import app.playlog.core.data.UserSettingsRepository
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.ui.PlaytimeFormat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate

/** Home-screen widget: what is being played, this week's hours (and goal), the streak. */
class PlaylogWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = runCatching { load(context) }.getOrNull() ?: WidgetSnapshot.Empty
        provideContent { WidgetContent(context, snapshot) }
    }

    private suspend fun load(context: Context): WidgetSnapshot {
        val deps = EntryPointAccessors.fromApplication(context, WidgetDeps::class.java)
        val clock = deps.clock()
        val today = LocalDate.ofInstant(clock.now(), clock.zone())
        val nowPlaying = deps.library().observeLibrary().first().filter { it.lastPlayedAt != null }.maxByOrNull { it.lastPlayedAt!! }
        val week = deps.activity().observeSummary(ActivityPeriod.WEEK, today).first().total
        val settings = deps.settings().settings.first()
        return WidgetSnapshot(
            nowPlaying = nowPlaying?.title,
            week = week,
            streak = deps.activity().observeStreak().first(),
            goalPercent = settings.weeklyGoalHours.takeIf { it > 0 }?.let { (week.toMinutes() * Percent / (it * MinutesPerHour)).toInt() },
        )
    }

    companion object {
        /** Redraws every placed widget; called after a sync. */
        suspend fun refresh(context: Context) = runCatching { PlaylogWidget().updateAll(context) }
    }
}

class PlaylogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaylogWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDeps {
    fun library(): LibraryRepository
    fun activity(): ActivityRepository
    fun settings(): UserSettingsRepository
    fun clock(): AppClock
}

internal data class WidgetSnapshot(val nowPlaying: String?, val week: Duration, val streak: Int, val goalPercent: Int?) {
    companion object {
        val Empty = WidgetSnapshot(null, Duration.ZERO, 0, null)
    }
}

@Composable
private fun WidgetContent(context: Context, snapshot: WidgetSnapshot) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(context.getString(R.string.widget_now_playing), style = TextStyle(color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        Text(
            snapshot.nowPlaying ?: context.getString(R.string.widget_nothing),
            style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
        )
        Spacer(GlanceModifier.height(10.dp))
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Stat(context.getString(R.string.widget_week, playtime(context, snapshot.week)), Lime)
            snapshot.goalPercent?.let {
                Spacer(GlanceModifier.width(12.dp))
                Stat(context.getString(R.string.widget_goal, it), Mint)
            }
            if (snapshot.streak > 0) {
                Spacer(GlanceModifier.width(12.dp))
                Stat(context.getString(R.string.widget_streak, snapshot.streak), Orange)
            }
        }
    }
}

@Composable
private fun Stat(text: String, color: ColorProvider) {
    Text(text, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
}

private fun playtime(context: Context, duration: Duration): String = when (val v = PlaytimeFormat.of(duration)) {
    is PlaytimeFormat.Value.Minutes -> context.getString(app.playlog.core.ui.R.string.duration_minutes, v.minutes)
    is PlaytimeFormat.Value.Hours -> context.getString(app.playlog.core.ui.R.string.duration_hours, v.text)
}

private val Lime = ColorProvider(Color(0xFFC8F55A))
private val Mint = ColorProvider(Color(0xFF5EE6B0))
private val Orange = ColorProvider(Color(0xFFFFA24C))
private val White = ColorProvider(Color.White)
private const val Percent = 100
private const val MinutesPerHour = 60
