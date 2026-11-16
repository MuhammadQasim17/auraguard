package com.auraguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.auraguard.app.data.local.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity): Long

    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE resolved = 0 ORDER BY timestamp DESC")
    fun getUnresolvedIncidents(): Flow<List<IncidentEntity>>

    @Query("UPDATE incidents SET resolved = :resolved WHERE id = :incidentId")
    suspend fun setIncidentResolvedStatus(incidentId: Long, resolved: Boolean)

    @Query("UPDATE incidents SET resolved = 1 WHERE id = :incidentId")
    suspend fun resolveIncident(incidentId: Long)

    @Update
    suspend fun updateIncident(incident: IncidentEntity)

    @Delete
    suspend fun deleteIncident(incident: IncidentEntity)
}
