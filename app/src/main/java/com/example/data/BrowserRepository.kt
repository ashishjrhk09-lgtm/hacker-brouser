package com.example.data

import kotlinx.coroutines.flow.Flow

class BrowserRepository(private val browserDao: BrowserDao) {

    val settings: Flow<BrowserSettings?> = browserDao.getSettingsFlow()

    val extensions: Flow<List<Extension>> = browserDao.getAllExtensionsFlow()

    val logs: Flow<List<ConsoleLog>> = browserDao.getAllLogsFlow()

    val history: Flow<List<HistoryItem>> = browserDao.getAllHistoryFlow()

    suspend fun insertHistory(item: HistoryItem) {
        browserDao.insertHistory(item)
    }

    suspend fun clearHistory() {
        browserDao.clearHistory()
    }

    suspend fun clearAllExtensions() {
        browserDao.clearAllExtensions()
    }

    suspend fun getSettingsDirect(): BrowserSettings? {
        return browserDao.getSettingsDirect()
    }

    suspend fun saveSettings(settings: BrowserSettings) {
        browserDao.saveSettings(settings)
    }

    suspend fun getActiveExtensionsDirect(): List<Extension> {
        return browserDao.getActiveExtensionsDirect()
    }

    suspend fun insertExtension(extension: Extension) {
        browserDao.insertExtension(extension)
    }

    suspend fun deleteExtensionById(id: Int) {
        browserDao.deleteExtensionById(id)
    }

    suspend fun updateExtensionStatus(id: Int, isEnabled: Boolean) {
        browserDao.updateExtensionStatus(id, isEnabled)
    }

    suspend fun insertLog(level: String, message: String, sourceId: String, lineNumber: Int) {
        browserDao.insertLog(
            ConsoleLog(
                level = level,
                message = message,
                sourceId = sourceId,
                lineNumber = lineNumber
            )
        )
    }

    suspend fun clearLogs() {
        browserDao.clearLogs()
    }
}
