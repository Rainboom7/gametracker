package app.playlog.di

import android.content.Context
import android.util.Log
import app.playlog.BuildConfig
import app.playlog.core.common.AppClock
import app.playlog.core.common.SystemAppClock
import app.playlog.core.data.AccountRepository
import app.playlog.core.data.AccountStore
import app.playlog.core.data.ActivityRepository
import app.playlog.core.data.DefaultAccountRepository
import app.playlog.core.data.DefaultActivityRepository
import app.playlog.core.data.DefaultDiscoverRepository
import app.playlog.core.data.BadgesRepository
import app.playlog.core.data.DefaultBadgesRepository
import app.playlog.core.data.DefaultGameNotesRepository
import app.playlog.core.data.DefaultTodayRepository
import app.playlog.core.data.TodayRepository
import app.playlog.core.data.GameNotesRepository
import app.playlog.core.database.FileBadgeStore
import app.playlog.core.database.FileGameNotesStore
import app.playlog.core.data.DiscoverRepository
import app.playlog.core.data.AskEngine
import app.playlog.core.data.DealsRepository
import app.playlog.core.data.DefaultReleasesRepository
import app.playlog.core.data.ReleasesRepository
import app.playlog.core.data.DefaultStoreDealsRepository
import app.playlog.core.data.StoreDealsRepository
import app.playlog.core.data.DefaultDealsRepository
import app.playlog.core.data.DefaultGiveawaysRepository
import app.playlog.core.data.DefaultWishlistRepository
import app.playlog.core.data.GiveawaysRepository
import app.playlog.core.data.PriceHistory
import app.playlog.core.data.WishlistRepository
import app.playlog.core.data.DefaultGameMetadataRepository
import app.playlog.core.data.DefaultLibraryRepository
import app.playlog.core.data.DefaultScreenshotRepository
import app.playlog.core.data.GameMetadataRepository
import app.playlog.core.data.LibraryRepository
import app.playlog.core.data.LibraryStore
import app.playlog.core.data.ScreenshotRepository
import app.playlog.core.data.SyncEngine
import app.playlog.core.data.SyncScheduler
import app.playlog.core.data.UserSettingsRepository
import app.playlog.core.database.DataStoreUserSettings
import app.playlog.core.database.KeystoreCredentialStore
import app.playlog.core.database.PlaylogDatabase
import app.playlog.core.database.RoomAccountStore
import app.playlog.core.database.RoomLibraryStore
import app.playlog.core.network.AiaTrustManager
import app.playlog.core.network.HttpClientFactory
import app.playlog.platform.api.CredentialStore
import app.playlog.platform.api.GamePlatformSource
import app.playlog.platform.deals.CheapSharkPriceHistory
import app.playlog.platform.deals.DealsDiskCache
import app.playlog.platform.deals.FileGiveawaysStore
import app.playlog.platform.deals.FileWishlistStore
import app.playlog.platform.deals.OnlineGiveawaysSource
import app.playlog.platform.deals.FileDealsRegionStore
import app.playlog.platform.deals.NintendoStoreDeals
import app.playlog.platform.deals.PlayStationStoreDeals
import app.playlog.platform.deals.SteamSpyClient
import app.playlog.platform.deals.SteamStoreDeals
import app.playlog.platform.deals.SteamTagSearch
import app.playlog.platform.deals.SteamUpcoming
import app.playlog.platform.deals.OnlineDealsSource
import app.playlog.platform.metadata.FileMetadataStore
import app.playlog.platform.metadata.OnlineMetadataSource
import app.playlog.platform.nintendo.NintendoSource
import app.playlog.platform.playstation.PlayStationSource
import app.playlog.platform.steam.SteamSource
import app.playlog.sync.WorkManagerSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Singleton
import javax.net.ssl.SSLContext

/** Store requests, statuses and shapes (no tokens); `adb logcat -s Playlog/deals` to see why a store shows nothing. */
private val DealsLog: (String) -> Unit = { Log.i("Playlog/deals", it) }

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private const val LIKED_RATING = 8

    @Provides
    @Singleton
    fun clock(): AppClock = SystemAppClock

    @Provides
    @Singleton
    fun httpClient(): HttpClient =
        HttpClientFactory.create(
            OkHttp.create {
                // Nintendo's play history host sends no intermediate certificate; see AiaTrustManager.
                config {
                    val trustManager = AiaTrustManager()
                    val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
                    sslSocketFactory(ssl.socketFactory, trustManager)
                }
            },
            enableLogging = BuildConfig.DEBUG, logSink = { Log.d("Playlog/http", it) })

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PlaylogDatabase = PlaylogDatabase.create(context)

    @Provides
    @Singleton
    fun credentialStore(@ApplicationContext context: Context): CredentialStore = KeystoreCredentialStore(context)

    @Provides
    @Singleton
    fun libraryStore(db: PlaylogDatabase): LibraryStore = RoomLibraryStore(db)

    @Provides
    @Singleton
    fun accountStore(db: PlaylogDatabase): AccountStore = RoomAccountStore(db)

    @Provides
    @Singleton
    fun userSettings(@ApplicationContext context: Context): UserSettingsRepository = DataStoreUserSettings(context)

    /** Every supported platform. Adding one means writing a `:platform:x` module and listing it here. */
    @Provides
    @Singleton
    fun sources(client: HttpClient, credentials: CredentialStore, clock: AppClock): Set<GamePlatformSource> = setOf(
        SteamSource(client, credentials),
        PlayStationSource.create(client, credentials, clock, diagnostics = { if (BuildConfig.DEBUG) Log.d("Playlog/psn", it) }),
        NintendoSource.create(client, credentials, clock),
    )

    @Provides
    @Singleton
    fun syncEngine(sources: Set<@JvmSuppressWildcards GamePlatformSource>, library: LibraryStore, accounts: AccountStore, clock: AppClock) =
        SyncEngine(sources, library, accounts, clock)

    @Provides
    @Singleton
    fun libraryRepository(store: LibraryStore): LibraryRepository = DefaultLibraryRepository(store)

    @Provides
    @Singleton
    fun activityRepository(store: LibraryStore, library: LibraryRepository, clock: AppClock): ActivityRepository =
        DefaultActivityRepository(store, library, clock)

    @Provides
    @Singleton
    fun discoverRepository(store: LibraryStore, library: LibraryRepository, clock: AppClock, notes: GameNotesRepository): DiscoverRepository =
        DefaultDiscoverRepository(store, library, clock, liked = { notes.observeAll().first().filterValues { (it.rating ?: 0) >= LIKED_RATING }.keys })

    @Provides
    @Singleton
    fun screenshotRepository(store: LibraryStore): ScreenshotRepository = DefaultScreenshotRepository(store)

    @Provides
    @Singleton
    fun gameNotesRepository(@ApplicationContext context: Context, clock: AppClock): GameNotesRepository =
        DefaultGameNotesRepository(FileGameNotesStore(File(context.filesDir, "game-notes.json")), clock)

    @Provides
    @Singleton
    fun badgesRepository(
        @ApplicationContext context: Context,
        discover: DiscoverRepository,
        screenshots: ScreenshotRepository,
        notes: GameNotesRepository,
        clock: AppClock,
    ): BadgesRepository = DefaultBadgesRepository(discover, screenshots, notes, FileBadgeStore(File(context.filesDir, "badges.json")), clock)

    /** HowLongToBeat and Steam store facts for the game page, cached as files in the cache dir. */
    @Provides
    @Singleton
    fun storeDealsRepository(
        @ApplicationContext context: Context,
        client: HttpClient,
        sources: Set<@JvmSuppressWildcards GamePlatformSource>,
        library: LibraryRepository,
    ): StoreDealsRepository = DefaultStoreDealsRepository(
        stores = setOf(
            SteamStoreDeals(client, log = DealsLog),
            PlayStationStoreDeals(client, log = DealsLog),
            NintendoStoreDeals(client, log = DealsLog),
        ),
        platformSources = sources,
        regions = FileDealsRegionStore(File(context.filesDir, "deals-regions.json")),
        library = library,
        deviceCountry = { java.util.Locale.getDefault().country },
        onError = { platform, e -> Log.w("Playlog/deals", "Store deals failed for $platform", e) },
    )

    @Provides
    @Singleton
    fun wishlistRepository(
        @ApplicationContext context: Context,
        stores: StoreDealsRepository,
        sources: Set<@JvmSuppressWildcards GamePlatformSource>,
        clock: AppClock,
    ): WishlistRepository = DefaultWishlistRepository(
        store = FileWishlistStore(File(context.filesDir, "wishlist.json")),
        stores = stores,
        platformSources = sources,
        clock = clock,
        onError = { platform, e -> Log.w("Playlog/deals", "Wishlist $platform failed", e) },
    )

    /** Free-text "Спроси" search: an offline parser plus the Steam tag search, no keys. */
    @Provides
    @Singleton
    fun askEngine(client: HttpClient, library: LibraryRepository, deals: DealsRepository, stores: StoreDealsRepository): AskEngine =
        AskEngine(
            finder = SteamTagSearch(client, SteamStoreDeals(client, log = DealsLog), SteamSpyClient(client), log = DealsLog),
            library = library,
            deals = deals,
            stores = stores,
        )

    @Provides
    @Singleton
    fun giveawaysRepository(@ApplicationContext context: Context, client: HttpClient, library: LibraryRepository, clock: AppClock): GiveawaysRepository =
        DefaultGiveawaysRepository(
            source = OnlineGiveawaysSource(client, country = { java.util.Locale.getDefault().country }, log = DealsLog),
            store = FileGiveawaysStore(File(context.filesDir, "giveaways.json")),
            library = library,
            clock = clock,
            onError = { Log.w("Playlog/deals", "Giveaways refresh failed", it) },
        )

    @Provides
    @Singleton
    fun todayRepository(
        store: LibraryStore,
        library: LibraryRepository,
        wishlist: WishlistRepository,
        giveaways: GiveawaysRepository,
        clock: AppClock,
    ): TodayRepository = DefaultTodayRepository(store, library, wishlist, giveaways, clock)

    @Provides
    @Singleton
    fun releasesRepository(client: HttpClient, stores: StoreDealsRepository, clock: AppClock): ReleasesRepository =
        DefaultReleasesRepository(
            source = SteamUpcoming(client, SteamStoreDeals(client, log = DealsLog), log = DealsLog),
            stores = stores,
            clock = clock,
            onError = { e -> Log.w("Playlog/deals", "Upcoming releases failed", e) },
        )

    @Provides
    @Singleton
    fun priceHistory(client: HttpClient): PriceHistory = CheapSharkPriceHistory(client)

    @Provides
    @Singleton
    fun dealsRepository(@ApplicationContext context: Context, client: HttpClient, library: LibraryRepository, clock: AppClock): DealsRepository {
        val cache = DealsDiskCache(File(context.cacheDir, "deals"))
        return DefaultDealsRepository(
            source = OnlineDealsSource(client, cache, onError = { what, e -> Log.w("Playlog", "Deals: $what failed", e) }),
            library = library,
            cache = cache,
            clock = clock,
            onError = { e -> Log.w("Playlog", "Deals refresh failed", e) },
        )
    }

    @Provides
    @Singleton
    fun gameMetadataRepository(@ApplicationContext context: Context, client: HttpClient, clock: AppClock): GameMetadataRepository =
        DefaultGameMetadataRepository(
            source = OnlineMetadataSource(client, clock),
            store = FileMetadataStore(File(context.cacheDir, "metadata")),
            clock = clock,
            onError = { query, e -> Log.w("Playlog", "Metadata lookup failed for ${query.title}", e) },
        )

    @Provides
    @Singleton
    fun accountRepository(sources: Set<@JvmSuppressWildcards GamePlatformSource>, accounts: AccountStore, library: LibraryStore): AccountRepository =
        DefaultAccountRepository(sources, accounts, library)

    @Provides
    @Singleton
    fun syncScheduler(@ApplicationContext context: Context): SyncScheduler = WorkManagerSyncScheduler(context)
}
