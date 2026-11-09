package com.payup.data

import androidx.room3.Database
import androidx.room3.ConstructedBy
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [FriendEntity::class, GameEntity::class, PlayerEntity::class, BuyInEntity::class, TransferEntity::class],
    version = 5,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PayUpDao

    companion object {
        /** Every migration, oldest first. Each platform's builder registers all of them. */
        val MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        /** v5: shared games (online id, whether this device hosts it, the host secret). Existing games are not shared. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE games ADD COLUMN shareId TEXT")
                connection.execSQL("ALTER TABLE games ADD COLUMN isHost INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE games ADD COLUMN hostKey TEXT")
                connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_games_shareId` ON `games` (`shareId`)")
            }
        }

        /** v4: each game keeps its own currency; every earlier game was in shekels. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE games ADD COLUMN currency TEXT NOT NULL DEFAULT 'ILS'")
            }
        }

        /** v3: the dealer picked by the card deal. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE games ADD COLUMN dealerPlayerId INTEGER")
            }
        }

        /** v2: money moves from whole shekels to agorot (x100), and chip counting is added. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("UPDATE buy_ins SET amount = amount * 100")
                connection.execSQL("UPDATE games SET defaultBuyIn = defaultBuyIn * 100, voidedDifference = voidedDifference * 100")
                connection.execSQL("UPDATE players SET finalAmount = finalAmount * 100, net = net * 100") // NULL stays NULL
                connection.execSQL("UPDATE transfers SET amount = amount * 100")
                connection.execSQL("ALTER TABLE games ADD COLUMN chipValue INTEGER")
                connection.execSQL("ALTER TABLE games ADD COLUMN roundToWhole INTEGER NOT NULL DEFAULT 1")
                connection.execSQL("ALTER TABLE players ADD COLUMN chips INTEGER")
            }
        }
    }
}

// Room generates the actual implementation for each platform.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
