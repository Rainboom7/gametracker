package app.playlog.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GameEntity::class, SnapshotEntity::class, DailyEntity::class, ScreenshotEntity::class, AccountEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PlaylogDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun activity(): ActivityDao
    abstract fun screenshots(): ScreenshotDao
    abstract fun accounts(): AccountDao

    companion object {
        fun create(context: Context): PlaylogDatabase =
            Room.databaseBuilder(context, PlaylogDatabase::class.java, "playlog.db").build()

        fun inMemory(context: Context): PlaylogDatabase =
            Room.inMemoryDatabaseBuilder(context, PlaylogDatabase::class.java).allowMainThreadQueries().build()
    }
}
