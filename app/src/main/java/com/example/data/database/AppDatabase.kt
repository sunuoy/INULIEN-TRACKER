package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.GlucoseDao
import com.example.data.dao.InsulinDao
import com.example.data.dao.ProfileDao
import com.example.data.dao.ReminderDao
import com.example.data.dao.CartridgeRefillLogDao
import com.example.data.model.GlucoseReading
import com.example.data.model.InsulinRecord
import com.example.data.model.Reminder
import com.example.data.model.UserProfile
import com.example.data.model.CartridgeRefillLog

@Database(
    entities = [
        InsulinRecord::class,
        GlucoseReading::class,
        Reminder::class,
        UserProfile::class,
        CartridgeRefillLog::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun insulinDao(): InsulinDao
    abstract fun glucoseDao(): GlucoseDao
    abstract fun reminderDao(): ReminderDao
    abstract fun profileDao(): ProfileDao
    abstract fun cartridgeRefillLogDao(): CartridgeRefillLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "glucolog_database"
                )
                .fallbackToDestructiveMigration() // ensures safety during dev schema modifications
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
