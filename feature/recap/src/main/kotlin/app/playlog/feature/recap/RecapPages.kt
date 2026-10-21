package app.playlog.feature.recap

import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.ActivitySummary
import app.playlog.core.model.DayTotal
import app.playlog.core.model.Platform
import app.playlog.core.model.TopGame
import java.time.Duration
import java.time.LocalDate

/** One full-screen story card of a recap. */
sealed interface RecapPage {
    data class Intro(val period: ActivityPeriod, val start: LocalDate, val endInclusive: LocalDate) : RecapPage
    data class Total(val total: Duration, val previous: Duration, val activeDays: Int, val daysInPeriod: Int) : RecapPage
    data class TopGameOf(val top: TopGame) : RecapPage
    data class Ranking(val games: List<TopGame>) : RecapPage
    data class Platforms(val shares: List<Pair<Platform, Float>>, val leader: Platform) : RecapPage
    data class Rhythm(val bestDay: DayTotal?, val gamesPlayed: Int, val longestStreak: Int) : RecapPage
    data class Vibe(val tags: List<String>) : RecapPage
    data class Outro(val summary: RecapSummary) : RecapPage
    data object Empty : RecapPage
}

/** What the shareable final card shows. */
data class RecapSummary(
    val period: ActivityPeriod,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val total: Duration,
    val topGame: String?,
    val gamesPlayed: Int,
    val activeDays: Int,
    val vibe: List<String>,
)

object RecapPages {
    private const val RANKING = 5
    private const val VIBE = 3

    /** Story order; pages without data are skipped so a quiet week is short, not empty-looking. */
    fun build(summary: ActivitySummary, longestStreak: Int, taste: List<String>): List<RecapPage> {
        if (summary.total.isZero) return listOf(RecapPage.Intro(summary.period, summary.start, summary.endInclusive), RecapPage.Empty)
        val days = (summary.endInclusive.toEpochDay() - summary.start.toEpochDay() + 1).toInt()
        val vibe = taste.take(VIBE)
        val pages = mutableListOf<RecapPage>(
            RecapPage.Intro(summary.period, summary.start, summary.endInclusive),
            RecapPage.Total(summary.total, summary.previousTotal, summary.activeDays, days),
        )
        summary.topGames.firstOrNull()?.let { pages += RecapPage.TopGameOf(it) }
        if (summary.topGames.size > 1) pages += RecapPage.Ranking(summary.topGames.take(RANKING))
        val totalMinutes = summary.total.toMinutes().toFloat()
        val shares = summary.perPlatform.entries
            .filter { !it.value.isZero }
            .sortedByDescending { it.value }
            .map { it.key to it.value.toMinutes() / totalMinutes }
        if (shares.isNotEmpty()) pages += RecapPage.Platforms(shares, shares.first().first)
        pages += RecapPage.Rhythm(summary.bestDay, summary.gamesPlayed, longestStreak)
        if (vibe.isNotEmpty()) pages += RecapPage.Vibe(vibe)
        pages += RecapPage.Outro(
            RecapSummary(
                period = summary.period,
                start = summary.start,
                endInclusive = summary.endInclusive,
                total = summary.total,
                topGame = summary.topGames.firstOrNull()?.entry?.title,
                gamesPlayed = summary.gamesPlayed,
                activeDays = summary.activeDays,
                vibe = vibe,
            ),
        )
        return pages
    }
}
