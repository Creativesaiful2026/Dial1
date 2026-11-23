package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface T9ContactCacheDao {
    @Query("SELECT * FROM t9_contact_cache")
    fun observeAll(): Flow<List<T9ContactCacheEntity>>

    @Query("SELECT * FROM t9_contact_cache")
    suspend fun getAllOnce(): List<T9ContactCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<T9ContactCacheEntity>)

    @Query("DELETE FROM t9_contact_cache")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<T9ContactCacheEntity>) {
        clearAll()
        if (items.isNotEmpty()) insertAll(items)
    }
}

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dial_entries ORDER BY slot ASC")
    fun observeAll(): Flow<List<SpeedDialEntity>>

    @Query("SELECT * FROM speed_dial_entries ORDER BY slot ASC")
    suspend fun getAllOnce(): List<SpeedDialEntity>

    @Query("SELECT * FROM speed_dial_entries WHERE slot = :slot LIMIT 1")
    suspend fun getBySlot(slot: Int): SpeedDialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SpeedDialEntity)

    @Query("DELETE FROM speed_dial_entries WHERE slot = :slot")
    suspend fun deleteSlot(slot: Int)

    @Query("DELETE FROM speed_dial_entries")
    suspend fun clearAll()
}

@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM blocked_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM blocked_rules WHERE isEnabled = 1")
    suspend fun getEnabledRulesOnce(): List<BlockRuleEntity>

    @Query("SELECT * FROM blocked_rules ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<BlockRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: BlockRuleEntity): Long

    @Query("DELETE FROM blocked_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blocked_rules")
    suspend fun clearAll()
}

@Dao
interface BlockedCallEventDao {
    @Query("SELECT * FROM blocked_call_events ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<BlockedCallEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: BlockedCallEventEntity)

    @Query("DELETE FROM blocked_call_events")
    suspend fun clearAll()
}

@Dao
interface CallNoteDao {
    @Query("SELECT * FROM call_notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CallNoteEntity>>

    @Query("SELECT * FROM call_notes WHERE normalizedNumber = :normalizedNumber ORDER BY updatedAt DESC")
    fun observeByNumber(normalizedNumber: String): Flow<List<CallNoteEntity>>

    @Query("SELECT * FROM call_notes ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<CallNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: CallNoteEntity): Long

    @Query("DELETE FROM call_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_notes")
    suspend fun clearAll()
}

@Dao
interface CallReminderDao {
    @Query("SELECT * FROM call_reminders ORDER BY triggerAtMillis ASC")
    fun observeAll(): Flow<List<CallReminderEntity>>

    @Query("SELECT * FROM call_reminders WHERE status = 'PENDING'")
    suspend fun getPendingOnce(): List<CallReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: CallReminderEntity): Long

    @Query("UPDATE call_reminders SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE call_reminders SET workRequestUuid = :uuid WHERE id = :id")
    suspend fun updateWorkUuid(id: Long, uuid: String?)

    @Query("DELETE FROM call_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface QuickResponseDao {
    @Query("SELECT * FROM quick_responses ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<QuickResponseEntity>>

    @Query("SELECT * FROM quick_responses ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllOnce(): List<QuickResponseEntity>

    @Query("SELECT COUNT(*) FROM quick_responses")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QuickResponseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: QuickResponseEntity): Long

    @Query("DELETE FROM quick_responses WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ContactExtensionDao {
    @Query("SELECT * FROM contact_extensions")
    fun observeAll(): Flow<List<ContactExtensionEntity>>

    @Query("SELECT * FROM contact_extensions WHERE contactLookupKey = :lookupKey LIMIT 1")
    suspend fun getByLookupKey(lookupKey: String): ContactExtensionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ext: ContactExtensionEntity)
}

@Dao
interface PrivateContactDao {
    @Query("SELECT * FROM private_contacts ORDER BY displayName ASC")
    fun observeAll(): Flow<List<PrivateContactEntity>>

    @Query("SELECT * FROM private_contacts ORDER BY displayName ASC")
    suspend fun getAllOnce(): List<PrivateContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: PrivateContactEntity): Long

    @Query("DELETE FROM private_contacts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM private_contacts")
    suspend fun clearAll()
}

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    suspend fun getEnabledOnce(): List<AutomationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AutomationRuleEntity): Long

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CustomThemeDao {
    @Query("SELECT * FROM custom_themes ORDER BY isBuiltIn DESC, updatedAt DESC")
    fun observeAll(): Flow<List<CustomThemeEntity>>

    @Query("SELECT COUNT(*) FROM custom_themes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(theme: CustomThemeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(themes: List<CustomThemeEntity>)

    @Query("DELETE FROM custom_themes WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomById(id: String)
}
