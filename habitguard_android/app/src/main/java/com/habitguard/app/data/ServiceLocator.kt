package com.habitguard.app.data

import android.content.Context
import androidx.room.Room

object ServiceLocator {
    @Volatile private var database: HabitGuardDatabase? = null

    fun database(context: Context): HabitGuardDatabase =
        database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                HabitGuardDatabase::class.java,
                "habitguard.db",
            )
                .addMigrations(
                    HabitGuardDatabaseMigrations.MIGRATION_7_11,
                    HabitGuardDatabaseMigrations.MIGRATION_10_11,
                )
                .build()
                .also { database = it }
        }
}
