package app.playlog.core.data

import app.playlog.core.model.AskIntent
import app.playlog.core.model.Platform
import app.playlog.core.model.PlayFeature

/**
 * Turns a free-text wish ("игра на 2 на 1 экране", "уютное на вечер", "рогалик как Hades") into an
 * [AskIntent] with Steam tags and store categories. Rule based and offline: no model, no keys. Russian
 * words are matched by stem, so "кооператив", "кооп" and "кооперативную" all hit the same rule.
 */
object AskParser {
    private class Rule(
        pattern: String,
        val tags: List<String> = emptyList(),
        val features: Set<PlayFeature> = emptySet(),
        val apply: (Draft) -> Unit = {},
    ) {
        val regex = Regex("(?U)$pattern")
    }

    private class Draft {
        /** Tag → where in the text it was asked for; the order the user wrote things in is the priority. */
        val tagAt = LinkedHashMap<String, Int>()
        val tags = LinkedHashSet<String>()
        val features = LinkedHashSet<PlayFeature>()
        var players: Int? = null
        val platforms = LinkedHashSet<Platform>()
        var onSale = false
        var free = false
        var ownedOnly = false
        var unplayed = false
        var local = false
        var online = false
        var versus = false
    }

    private const val LOCAL_COOP = "Local Co-Op"
    private const val COOP = "Co-op"
    private const val ONLINE_COOP = "Online Co-Op"
    private val modeTags = setOf(LOCAL_COOP, COOP, ONLINE_COOP, "PvP", "Party Game")

    private val rules = listOf(
        // Where and with whom.
        Rule("""(одн(ом|ого)|1|общ(ем|ий)) ?экран|сплит|split ?screen|shared ?screen|couch|на диване|диванн""", listOf(LOCAL_COOP)) { it.local = true },
        Rule("""локальн|local|за одн(им|ой) (компом|консолью|приставкой)|одн(а|ой) приставк""", listOf(LOCAL_COOP)) { it.local = true },
        Rule("""кооп|co-?op|вместе|с друз|с другом|с подруг|напарник""", listOf(COOP)),
        Rule(
            """с (девушк|парн|жен|муж|ребенк|детьми|сыном|дочк|семь)|семейн|для всей семьи|family""",
            listOf(LOCAL_COOP, "Family Friendly"),
        ) { it.local = true },
        Rule("""онлайн|по сети|online|по интернету""") { it.online = true },
        Rule("""пвп|pvp|против друг|друг против|versus|соревн|дуэл|1 ?(на|v|vs) ?1""", listOf("PvP")) { it.versus = true },
        Rule("""remote ?play|удаленн""", features = setOf(PlayFeature.REMOTE_PLAY_TOGETHER)),
        Rule("""геймпад|джойстик|контроллер|controller|gamepad""", features = setOf(PlayFeature.CONTROLLER)),
        Rule("""одиночн|в одиночку|сам(ому|о)? по себе|single ?player|сингл""", features = setOf(PlayFeature.SINGLE_PLAYER)),
        Rule("""вечеринк|пати|party|компани""", listOf("Party Game", LOCAL_COOP)) { it.local = true },
        // Genres and moods, Steam tag names.
        Rule("""рогалик|рогалит|roguelike|roguelite""", listOf("Roguelike", "Roguelite")),
        Rule("""метроидван|metroidvania""", listOf("Metroidvania")),
        Rule("""соулс|souls|дарк соулс""", listOf("Souls-like")),
        Rule("""сложн|хардкор|hardcore|difficult|чтоб(ы)? страдать""", listOf("Difficult")),
        Rule("""хоррор|ужас|страшн|horror|пуга""", listOf("Horror")),
        Rule("""головоломк|пазл|puzzle|подумать|логич""", listOf("Puzzle")),
        Rule("""гонк|гоночн|racing|тачк|машинк""", listOf("Racing")),
        Rule("""стратег|strategy|тактик|tactic""", listOf("Strategy")),
        Rule("""рпг|rpg|ролев""", listOf("RPG")),
        Rule("""шутер|shooter|стрелялк|fps""", listOf("Shooter")),
        Rule("""файтинг|fighting|драк|мордобой""", listOf("Fighting")),
        Rule("""платформер|platformer|прыгал""", listOf("Platformer")),
        Rule("""выживан|survival""", listOf("Survival")),
        Rule("""песочниц|sandbox""", listOf("Sandbox")),
        Rule("""крафт|craft""", listOf("Crafting")),
        Rule("""ферм|farming|огород""", listOf("Farming Sim")),
        Rule("""уютн|cozy|wholesome|ламповую|ламповое|ламповая""", listOf("Cozy", "Wholesome")),
        Rule("""расслаб|спокойн|релакс|relax|чил|медитатив|отдохнуть""", listOf("Relaxing")),
        Rule("""смешн|весел|угар|ржак|funny|comedy|юмор""", listOf("Funny", "Comedy")),
        Rule("""открыт(ый|ым) мир|open ?world""", listOf("Open World")),
        Rule("""пиксел|pixel""", listOf("Pixel Graphics")),
        Rule("""аниме|anime""", listOf("Anime")),
        Rule("""карточн|card|колод|деккилд|deck""", listOf("Deckbuilding", "Card Game")),
        Rule("""детектив|detective|расследов""", listOf("Detective")),
        Rule("""визуальн(ая|ую) новелл|новелл|visual novel""", listOf("Visual Novel")),
        Rule("""сюжет|истори|story|нарратив|кинематограф""", listOf("Story Rich")),
        Rule("""спорт|sport""", listOf("Sports")),
        Rule("""футбол|soccer|football""", listOf("Soccer")),
        Rule("""симулятор|simulat""", listOf("Simulation")),
        Rule("""градостро|city ?builder""", listOf("City Builder")),
        Rule("""строит|стройк|building""", listOf("Building")),
        Rule("""космос|космич|space""", listOf("Space")),
        Rule("""зомби|zombie""", listOf("Zombies")),
        Rule("""стелс|stealth|скрытн""", listOf("Stealth")),
        Rule("""ритм|rhythm|музык""", listOf("Rhythm")),
        Rule("""ретро|retro|олдскул""", listOf("Retro")),
        Rule("""коротк|на вечер|на пару час|на один вечер|быстро пройти|short""", listOf("Short")),
        Rule("""красив|атмосферн|atmospheric""", listOf("Atmospheric", "Beautiful")),
        Rule("""экшен|экшн|action""", listOf("Action")),
        Rule("""приключен|adventure""", listOf("Adventure")),
        Rule("""инди|indie""", listOf("Indie")),
        Rule("""мясо|кровав|gore""", listOf("Gore")),
        Rule("""динозавр""", listOf("Dinosaurs")),
        Rule("""кошк|кот(ик|ом|а)?\b|cats?\b""", listOf("Cats")),
        Rule("""собак|пес\b|dogs?\b""", listOf("Dog")),
        // Platforms, price, library.
        Rule("""свитч|switch|нинтендо|nintendo""") { it.platforms += Platform.NINTENDO },
        Rule("""плойк|плейстейшн|playstation|ps ?[45]|пс ?[45]""") { it.platforms += Platform.PLAYSTATION },
        Rule("""\bпк\b|\bpc\b|стим|steam|компьютер|комп\b""") { it.platforms += Platform.STEAM },
        Rule("""бесплатн|free|халяв""") { it.free = true },
        Rule("""скидк|дешев|распродаж|акци|sale|недорог""") { it.onSale = true },
        Rule("""из (моих|библиотеки|своих)|у меня (есть|уже)|в (моей )?библиотеке|мо(и|их) игр|уже купил""") { it.ownedOnly = true },
        Rule("""не (играл|начинал|запускал|трогал|пробовал)|не начат|бэклог|backlog|пылится|купил и забыл""") {
            it.unplayed = true
            it.ownedOnly = true
        },
    )

    private val playersRules = listOf(
        Regex("""(?U)(на|для) (2|двоих|двух|два)\b|вдво[её]м|2 игрок|two players?|2 ?players?|парой|в паре""") to 2,
        Regex("""(?U)(на|для) (3|тро(их|е)|тр[её]х)\b|втро[её]м|3 игрок""") to 3,
        Regex("""(?U)(на|для) (4|четверых|четыр[её]х)\b|вчетвером|4 игрок""") to 4,
    )

    private val similar = Regex("""(?U)(?:^|\s)(?:похож\w* на|вроде|типа|как в|как|like|наподобие|в духе)\s+(.+)$""")

    /** Filler words that never carry meaning on their own. */
    private val stopWords = setOf(
        "игра", "игру", "игры", "игр", "игрушка", "игрушку", "во", "что", "нибудь", "поиграть", "хочу", "хочется",
        "найди", "покажи", "посоветуй", "дай", "мне", "нам", "на", "в", "с", "и", "для", "по", "а", "какую",
        "какая", "чтобы", "чтоб", "можно", "game", "games", "a", "the", "for", "with", "to", "play", "some",
        "something", "есть", "бы", "типо", "plz", "пожалуйста", "одном", "одной", "экране", "экран", "1", "2",
    )

    fun parse(text: String): AskIntent {
        val normalized = normalize(text)
        val similarMatch = similar.find(normalized)
        val similarTo = similarMatch?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 }
        // The title after "как" is not a wish: "как Hades" must not turn into tags.
        val wish = if (similarMatch != null) normalized.substring(0, similarMatch.range.first) else normalized

        val d = Draft()
        val consumed = mutableListOf<IntRange>()
        for (rule in rules) {
            val hits = rule.regex.findAll(wish).toList()
            if (hits.isEmpty()) continue
            rule.tags.forEach { tag -> d.tagAt.putIfAbsent(tag, hits.first().range.first) }
            d.features += rule.features
            rule.apply(d)
            hits.forEach { consumed += it.range }
        }
        for ((regex, n) in playersRules) {
            regex.find(wish)?.let {
                d.players = d.players ?: n
                consumed += it.range
            }
        }
        d.tags += d.tagAt.entries.sortedBy { it.value }.map { it.key }
        resolvePlayMode(d)
        // Play modes already travel as store categories; genre and mood tags lead the search.
        val ordered = d.tags.filterNot { it in modeTags } + d.tags.filter { it in modeTags }

        val leftover = wish.indices.filter { i -> consumed.none { i in it } }
            .let { kept -> String(kept.map { wish[it] }.toCharArray()) }
            .split(' ')
            .filter { it.isNotBlank() && it !in stopWords && it.length > 1 }
            .joinToString(" ")

        return AskIntent(
            text = text.trim(),
            tags = ordered,
            features = d.features,
            players = d.players,
            platforms = d.platforms,
            onSale = d.onSale,
            free = d.free,
            ownedOnly = d.ownedOnly,
            unplayed = d.unplayed,
            similarTo = similarTo,
            leftover = leftover,
        )
    }

    /** Players, "один экран", "онлайн" and "против" combine into one store category. */
    private fun resolvePlayMode(d: Draft) {
        if (!d.wantsCompany()) return
        // "на двоих" alone: most people mean the same couch; keep online co-op out of the way.
        if (d.onlyCountsPlayers()) d.local = true
        if (d.local) d.tags += LOCAL_COOP
        val feature = playFeature(d)
        d.features += feature
        if (feature == PlayFeature.ONLINE_COOP) {
            d.tags.remove(LOCAL_COOP)
            d.tags += ONLINE_COOP
        }
        if (d.local || d.versus || d.online) d.tags.remove(COOP)
    }

    private fun Draft.mentionsCoop() = COOP in tags || LOCAL_COOP in tags

    private fun Draft.wantsCompany() = versus || mentionsCoop() || players != null || local || online

    private fun Draft.onlyCountsPlayers() = players != null && !online && !versus && !mentionsCoop()

    private fun playFeature(d: Draft): PlayFeature = when {
        d.versus && d.local -> PlayFeature.LOCAL_PVP
        d.versus -> PlayFeature.PVP
        d.local -> PlayFeature.SPLIT_SCREEN_COOP
        d.online -> PlayFeature.ONLINE_COOP
        else -> PlayFeature.COOP
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace('ё', 'е')
        .replace(Regex("""[^\p{L}\p{N}\- ]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
