package com.auraguard.app.data.repository

import com.auraguard.app.data.local.dao.IncidentDao
import com.auraguard.app.data.local.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

interface IncidentRepository {
    fun getAllIncidents(): Flow<List<IncidentEntity>>
    fun getUnresolvedIncidents(): Flow<List<IncidentEntity>>
    suspend fun insertIncident(incident: IncidentEntity): Long
    suspend fun resolveIncident(incidentId: Long)
    suspend fun setIncidentResolvedStatus(incidentId: Long, resolved: Boolean)
    suspend fun deleteIncident(incident: IncidentEntity)
}

class IncidentRepositoryImpl(
    private val incidentDao: IncidentDao,
) : IncidentRepository {

    override fun getAllIncidents(): Flow<List<IncidentEntity>> {
        return incidentDao.getAllIncidents()
    }

    override fun getUnresolvedIncidents(): Flow<List<IncidentEntity>> {
        return incidentDao.getUnresolvedIncidents()
    }

    override suspend fun insertIncident(incident: IncidentEntity): Long {
        return incidentDao.insertIncident(incident)
    }

    override suspend fun resolveIncident(incidentId: Long) {
        incidentDao.resolveIncident(incidentId)
    }

    override suspend fun setIncidentResolvedStatus(incidentId: Long, resolved: Boolean) {
        incidentDao.setIncidentResolvedStatus(incidentId, resolved)
    }

    override suspend fun deleteIncident(incident: IncidentEntity) {
        incidentDao.deleteIncident(incident)
    }
}
