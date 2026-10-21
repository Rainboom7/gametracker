package app.playlog.core.model

import java.time.Duration

/** One reason to open the app today, shown on the Home "Сегодня" card. */
sealed interface TodayItem {
    /** You played [entry] on this calendar day [years] years ago. */
    data class PlayedOnThisDay(val entry: LibraryEntry, val years: Int, val playtime: Duration) : TodayItem

    /** Captures taken on this calendar day [years] years ago; [entry] when the game is known. */
    data class ShotsOnThisDay(val shots: List<Screenshot>, val years: Int, val entry: LibraryEntry?) : TodayItem

    data class FirstPlayedAnniversary(val entry: LibraryEntry, val years: Int) : TodayItem

    /** A wishlisted game whose discount ends within a day or two. */
    data class SaleEndingSoon(val item: WishlistItem) : TodayItem

    data class GiveawayEndingSoon(val giveaway: Giveaway) : TodayItem
}
