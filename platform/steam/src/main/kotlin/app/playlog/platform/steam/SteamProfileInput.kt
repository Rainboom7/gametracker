package app.playlog.platform.steam

/** What the user typed in the "Steam profile" field. */
internal sealed interface SteamProfileInput {
    data class Id64(val steamId: String) : SteamProfileInput
    data class Vanity(val name: String) : SteamProfileInput

    companion object {
        private val ID64 = Regex("^7656\\d{13}$")
        private val PROFILES_URL = Regex("steamcommunity\\.com/profiles/(\\d{17})", RegexOption.IGNORE_CASE)
        private val VANITY_URL = Regex("steamcommunity\\.com/id/([^/?#]+)", RegexOption.IGNORE_CASE)

        fun parse(raw: String): SteamProfileInput? {
            val value = raw.trim()
            if (value.isEmpty()) return null
            if (ID64.matches(value)) return Id64(value)
            PROFILES_URL.find(value)?.let { return Id64(it.groupValues[1]) }
            VANITY_URL.find(value)?.let { return Vanity(it.groupValues[1]) }
            if (value.contains('/') || value.contains(' ')) return null
            return Vanity(value)
        }
    }
}
