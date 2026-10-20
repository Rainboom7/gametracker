package app.playlog.platform.api

import app.playlog.core.model.Account

/**
 * Connects a platform account. The UI calls [start], renders the returned [LinkFlow] (instructions
 * with links to open, and input fields), then passes what the user typed to [complete].
 */
interface AccountLinker {
    suspend fun start(): LinkFlow

    /** Validates the input, stores credentials and returns the linked profile. */
    suspend fun complete(flow: LinkFlow, input: Map<LinkFieldType, String>): Account

    /** The currently linked profile, or null. */
    suspend fun current(): Account?

    suspend fun unlink()
}

data class LinkFlow(
    val steps: List<LinkStep>,
    /** Fields the user fills in by hand (a fallback when a capture step exists). */
    val fields: List<LinkFieldType>,
    /** Opaque per-attempt state (e.g. a PKCE verifier) echoed back to [AccountLinker.complete]. */
    val state: Map<String, String> = emptyMap(),
)

/**
 * One instruction in the link flow. When [captureRedirectPrefix] is set the UI opens [url] in an
 * in-app WebView, watches navigations, and once one starts with the prefix puts the full URL into
 * [captureField] and completes the flow, so the user never copies tokens by hand.
 */
data class LinkStep(
    val type: LinkStepType,
    val url: String? = null,
    val captureRedirectPrefix: String? = null,
    val captureField: LinkFieldType? = null,
)

/** UI text for these lives in Android string resources, keyed by the enum name. */
enum class LinkStepType {
    STEAM_OPEN_API_KEY_PAGE,
    STEAM_MAKE_PROFILE_PUBLIC,
    PSN_SIGN_IN,
    PSN_OPEN_NPSSO_PAGE,
    NINTENDO_SIGN_IN,
    NINTENDO_COPY_LINK,
}

enum class LinkFieldType(val isSecret: Boolean) {
    /** 32 hex chars from steamcommunity.com/dev/apikey. */
    STEAM_API_KEY(isSecret = true),

    /** SteamID64, profile URL, or custom (vanity) URL name. */
    STEAM_PROFILE(isSecret = false),

    /** 64-char `npsso` value from ca.account.sony.com/api/v1/ssocookie. */
    PSN_NPSSO(isSecret = true),

    /** `com.scee.psxandroid.scecompcall://redirect/?code=…`, captured from the WebView sign-in. */
    PSN_REDIRECT_URL(isSecret = true),

    /** `npf…://auth#session_token_code=…` link copied from "Select this person". */
    NINTENDO_REDIRECT_URL(isSecret = true),
}
