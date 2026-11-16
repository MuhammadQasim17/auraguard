package com.auraguard.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.auraguard.app.data.TelemetryEntity
import com.auraguard.app.data.local.dao.EmergencyContactDao
import com.auraguard.app.data.local.dao.IncidentDao
import com.auraguard.app.data.local.dao.TelemetryDao
import com.auraguard.app.data.local.entity.EmergencyContactEntity
import com.auraguard.app.data.local.entity.IncidentEntity

@Database(
    entities = [
        IncidentEntity::class,
        EmergencyContactEntity::class,
        TelemetryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AuraGuardDatabase : RoomDatabase() {

    abstract fun incidentDao(): IncidentDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun telemetryDao(): TelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: AuraGuardDatabase? = null

        fun getDatabase(context: Context): AuraGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AuraGuardDatabase::class.java,
                    "auraguard_database",
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
