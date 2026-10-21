package app.playlog.platform.deals

/**
 * Store regions each client can list deals for. Russia and Belarus have no PlayStation or
 * Nintendo storefront since 2022, so they are left out there.
 */
object StoreRegions {
    /** Steam prices any `cc`; these are the common storefront currencies. */
    val STEAM = listOf(
        "RU", "KZ", "UA", "BY", "TR", "AR", "BR", "US", "CA", "MX", "GB", "DE", "FR", "IT", "ES", "PL", "NL", "SE", "NO", "CH",
        "CZ", "IN", "JP", "KR", "CN", "AU", "NZ", "ZA", "IL", "AE", "SA", "GE", "AM", "AZ", "UZ", "KG",
    )

    /** PlayStation Store: country → storefront locale. */
    val PLAYSTATION: Map<String, String> = linkedMapOf(
        "TR" to "en-tr", "UA" to "uk-ua", "PL" to "pl-pl", "US" to "en-us", "GB" to "en-gb", "DE" to "de-de", "FR" to "fr-fr",
        "IT" to "it-it", "ES" to "es-es", "PT" to "pt-pt", "NL" to "nl-nl", "BE" to "fr-be", "AT" to "de-at", "CH" to "de-ch",
        "CZ" to "cs-cz", "SK" to "sk-sk", "HU" to "hu-hu", "RO" to "ro-ro", "BG" to "bg-bg", "GR" to "el-gr", "SE" to "sv-se",
        "NO" to "no-no", "DK" to "da-dk", "FI" to "fi-fi", "IE" to "en-ie", "IL" to "en-il", "IN" to "en-in", "AE" to "en-ae",
        "SA" to "en-sa", "ZA" to "en-za", "AU" to "en-au", "NZ" to "en-nz", "CA" to "en-ca", "MX" to "es-mx", "BR" to "pt-br",
        "AR" to "es-ar", "CL" to "es-cl", "CO" to "es-co", "PE" to "es-pe", "JP" to "ja-jp", "KR" to "ko-kr", "HK" to "en-hk",
        "SG" to "en-sg", "TW" to "zh-hant-tw", "TH" to "en-th", "ID" to "en-id", "MY" to "en-my", "PH" to "en-ph",
    )

    /** Nintendo of Europe storefronts (the searching.nintendo-europe.com catalogue): country → search language. */
    val NINTENDO_EUROPE: Map<String, String> = linkedMapOf(
        "PL" to "en", "GB" to "en", "IE" to "en", "DE" to "de", "AT" to "de", "CH" to "de", "FR" to "fr", "BE" to "fr", "LU" to "fr",
        "NL" to "nl", "IT" to "it", "ES" to "es", "PT" to "pt", "CZ" to "en", "SK" to "en", "HU" to "en", "HR" to "en", "SI" to "en",
        "GR" to "en", "CY" to "en", "MT" to "en", "DK" to "en", "FI" to "en", "NO" to "en", "SE" to "en", "EE" to "en", "LV" to "en",
        "LT" to "en", "BG" to "en", "RO" to "en", "AU" to "en", "NZ" to "en", "ZA" to "en",
    )

    /** Nintendo of America storefronts (nintendo.com Algolia catalogue): country → index. */
    val NINTENDO_AMERICA: Map<String, String> = linkedMapOf(
        "US" to "store_game_en_us", "CA" to "store_game_en_ca", "MX" to "store_game_es_mx", "BR" to "store_game_pt_br",
    )

    val NINTENDO: List<String> = NINTENDO_EUROPE.keys.toList() + NINTENDO_AMERICA.keys
}
