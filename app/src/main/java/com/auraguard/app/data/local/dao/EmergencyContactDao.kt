package com.auraguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.auraguard.app.data.local.entity.EmergencyContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity): Long

    @Update
    suspend fun updateContact(contact: EmergencyContactEntity)

    @Delete
    suspend fun deleteContact(contact: EmergencyContactEntity)

    @Query("SELECT * FROM emergency_contacts ORDER BY id ASC")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryContact(): EmergencyContactEntity?

    @Query("UPDATE emergency_contacts SET isPrimary = 0")
    suspend fun clearAllPrimary()

    @Query("UPDATE emergency_contacts SET isPrimary = 1 WHERE id = :contactId")
    suspend fun setPrimary(contactId: Long)

    @Transaction
    suspend fun setSinglePrimaryContact(contactId: Long) {
        clearAllPrimary()
        setPrimary(contactId)
    }

    @Transaction
    suspend fun insertContactWithPrimaryCheck(contact: EmergencyContactEntity): Long {
        if (contact.isPrimary) {
            clearAllPrimary()
        }
        return insertContact(contact)
    }
}
