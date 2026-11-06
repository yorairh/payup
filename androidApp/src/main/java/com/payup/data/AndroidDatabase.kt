package com.payup.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver

/** The phone's own SQLite, so the existing payup.db file is opened exactly as before. */
fun AppDatabase.Companion.build(context: Context): AppDatabase =
    Room.databaseBuilder<AppDatabase>(context, "payup.db")
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(*MIGRATIONS)
        .build()
