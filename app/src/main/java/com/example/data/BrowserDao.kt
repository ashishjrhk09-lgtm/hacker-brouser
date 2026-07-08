package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserDao {
    @Query("SELECT * FROM browser_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<BrowserSettings?>

    @Query("SELECT * FROM browser_settings WHERE id = 1")
    suspend fun getSettingsDirect(): BrowserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: BrowserSettings)

    @Query("SELECT * FROM extensions ORDER BY timestamp DESC")
    fun getAllExtensionsFlow(): Flow<List<Extension>>

    @Query("SELECT * FROM extensions WHERE isEnabled = 1")
    suspend fun getActiveExtensionsDirect(): List<Extension>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(extension: Extension)

    @Query("DELETE FROM extensions WHERE id = :id")
    suspend fun deleteExtensionById(id: Int)

    @Query("UPDATE extensions SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateExtensionStatus(id: Int, isEnabled: Boolean)

    @Query("SELECT * FROM console_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogsFlow(): Flow<List<ConsoleLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ConsoleLog)

    @Query("DELETE FROM console_logs")
    suspend fun clearLogs()

    @Query("SELECT * FROM browser_history ORDER BY timestamp DESC LIMIT 200")
    fun getAllHistoryFlow(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryItem)

    @Query("DELETE FROM browser_history")
    suspend fun clearHistory()

    @Query("DELETE FROM extensions")
    suspend fun clearAllExtensions()
}
