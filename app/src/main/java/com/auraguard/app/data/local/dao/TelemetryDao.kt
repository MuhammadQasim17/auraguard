package com.auraguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auraguard.app.data.TelemetryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {
    @Query("SELECT * FROM telemetry_records ORDER BY timestamp DESC")
    fun getAllTelemetry(): Flow<List<TelemetryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetry(telemetry: TelemetryEntity)
}
