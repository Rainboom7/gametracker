package app.playlog.core.model

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

/** Where the user's playtime sits against HowLongToBeat's estimates. */
data class BeatProgress(
    /** Playtime as a share of the longest known estimate, 0..1. */
    val fraction: Float,
    /** Each known estimate as a share of the longest one, for tick marks on the bar. */
    val marks: List<Float>,
    val stage: Stage,
    /** Estimated time left to finish the main story; null once it is past. */
    val leftForStory: Duration?,
) {
    enum class Stage { STARTED, STORY_DONE, EXTRAS_DONE, COMPLETIONIST }
}

fun HowLongToBeat.progressFor(playtime: Duration): BeatProgress? {
    val estimates = listOfNotNull(mainStory, mainExtra, completionist)
    val longest = estimates.maxOrNull()?.takeIf { !it.isZero } ?: return null
    val stage = when {
        completionist != null && playtime >= completionist -> BeatProgress.Stage.COMPLETIONIST
        mainExtra != null && playtime >= mainExtra -> BeatProgress.Stage.EXTRAS_DONE
        mainStory != null && playtime >= mainStory -> BeatProgress.Stage.STORY_DONE
        else -> BeatProgress.Stage.STARTED
    }
    return BeatProgress(
        fraction = (playtime.seconds.toFloat() / longest.seconds).coerceIn(0f, 1f),
        marks = estimates.map { it.seconds.toFloat() / longest.seconds },
        stage = stage,
        leftForStory = mainStory?.minus(playtime)?.takeIf { !it.isNegative && !it.isZero },
    )
}

enum class LinkKind { STEAM_STORE, PLAYSTATION_STORE, NINTENDO_ESHOP, HOWLONGTOBEAT, METACRITIC }

data class ExternalLink(val kind: LinkKind, val url: String)

/** Store pages for every platform the title is owned on, then HowLongToBeat and Metacritic (exact page or search). */
fun LibraryEntry.externalLinks(metadata: GameMetadata?): List<ExternalLink> {
    val query = URLEncoder.encode(title, StandardCharsets.UTF_8).replace("+", "%20")
    val stores = games.sortedBy { it.id.platform.ordinal }.distinctBy { it.id.platform }.map { game ->
        val id = game.id.externalId
        when (game.id.platform) {
            Platform.STEAM -> ExternalLink(LinkKind.STEAM_STORE, "https://store.steampowered.com/app/$id")
            Platform.PLAYSTATION -> ExternalLink(
                LinkKind.PLAYSTATION_STORE,
                if (id.startsWith(PsnConceptPrefix)) {
                    "https://store.playstation.com/concept/${id.removePrefix(PsnConceptPrefix)}"
                } else {
                    "https://store.playstation.com/search/$query"
                },
            )
            Platform.NINTENDO -> ExternalLink(LinkKind.NINTENDO_ESHOP, "https://ec.nintendo.com/apps/$id/US")
        }
    }
    val hltb = ExternalLink(LinkKind.HOWLONGTOBEAT, metadata?.howLongToBeat?.url ?: "https://howlongtobeat.com/?q=$query")
    val metacritic = ExternalLink(
        LinkKind.METACRITIC,
        metadata?.criticScore?.url?.substringBefore('?') ?: "https://www.metacritic.com/search/$query/",
    )
    return stores + hltb + metacritic
}

private const val PsnConceptPrefix = "concept:"
