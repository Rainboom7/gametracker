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
import app.playlog.core.data.DiscoverRepository
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
import app.playlog.core.network.HttpClientFactory
import app.playlog.platform.api.CredentialStore
import app.playlog.platform.api.GamePlatformSource
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
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun clock(): AppClock = SystemAppClock

    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClientFactory.create(OkHttp.create(), enableLogging = BuildConfig.DEBUG)

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
        PlayStationSource.create(client, credentials, clock),
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
    fun discoverRepository(store: LibraryStore, library: LibraryRepository, clock: AppClock): DiscoverRepository =
        DefaultDiscoverRepository(store, library, clock)

    @Provides
    @Singleton
    fun screenshotRepository(store: LibraryStore): ScreenshotRepository = DefaultScreenshotRepository(store)

    /** HowLongToBeat and Steam store facts for the game page, cached as files in the cache dir. */
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
