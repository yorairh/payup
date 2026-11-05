package com.payup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FriendEntity::class, GameEntity::class, PlayerEntity::class, BuyInEntity::class, TransferEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PayUpDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "payup.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** v4: each game keeps its own currency; every earlier game was in shekels. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN currency TEXT NOT NULL DEFAULT 'ILS'")
            }
        }

        /** v3: the dealer picked by the card deal. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN dealerPlayerId INTEGER")
            }
        }

        /** v2: money moves from whole shekels to agorot (x100), and chip counting is added. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE buy_ins SET amount = amount * 100")
                db.execSQL("UPDATE games SET defaultBuyIn = defaultBuyIn * 100, voidedDifference = voidedDifference * 100")
                db.execSQL("UPDATE players SET finalAmount = finalAmount * 100, net = net * 100") // NULL stays NULL
                db.execSQL("UPDATE transfers SET amount = amount * 100")
                db.execSQL("ALTER TABLE games ADD COLUMN chipValue INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN roundToWhole INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE players ADD COLUMN chips INTEGER")
            }
        }
    }
}
