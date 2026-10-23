package com.kappi.crunch.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(entities = [DownloadItem::class], version = 1, exportSchema = false)
abstract class CrunchDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: CrunchDatabase? = null

        fun getInstance(context: Context): CrunchDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CrunchDatabase::class.java,
                    "crunch_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
