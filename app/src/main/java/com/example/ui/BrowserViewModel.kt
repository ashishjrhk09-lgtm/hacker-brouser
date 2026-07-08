package com.example.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class BrowserViewModel(
    private val application: Application,
    private val repository: BrowserRepository
) : AndroidViewModel(application) {

    val settings: StateFlow<BrowserSettings> = repository.settings
        .filterNotNull()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BrowserSettings()
        )

    val extensions: StateFlow<List<Extension>> = repository.extensions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val logs: StateFlow<List<ConsoleLog>> = repository.logs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val history: StateFlow<List<HistoryItem>> = repository.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Authorization flow for PIN secure lock
    private val _isAuthorized = MutableStateFlow(true)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    init {
        viewModelScope.launch {
            val current = repository.getSettingsDirect()
            if (current == null) {
                repository.saveSettings(BrowserSettings())
                _isAuthorized.value = true
            } else {
                _isAuthorized.value = !current.isPasscodeEnabled
            }
        }
    }

    fun verifyPasscode(pin: String): Boolean {
        val currentSettings = settings.value
        return if (currentSettings.isPasscodeEnabled && currentSettings.passcode == pin) {
            _isAuthorized.value = true
            true
        } else {
            false
        }
    }

    fun lockBrowser() {
        if (settings.value.isPasscodeEnabled) {
            _isAuthorized.value = false
        }
    }

    fun updateSettings(
        targetUrl: String,
        isUrlLocked: Boolean,
        isPasscodeEnabled: Boolean,
        passcode: String,
        enforceHttps: Boolean,
        clearCacheOnExit: Boolean,
        isDeveloperModeEnabled: Boolean
    ) {
        viewModelScope.launch {
            val updated = BrowserSettings(
                id = 1,
                targetUrl = targetUrl,
                isUrlLocked = isUrlLocked,
                isPasscodeEnabled = isPasscodeEnabled,
                passcode = passcode,
                enforceHttps = enforceHttps,
                clearCacheOnExit = clearCacheOnExit,
                isDeveloperModeEnabled = isDeveloperModeEnabled
            )
            repository.saveSettings(updated)
            if (!isPasscodeEnabled) {
                _isAuthorized.value = true
            }
        }
    }

    fun importExtensionFromFile(uri: Uri, resolver: ContentResolver, name: String, description: String, extensionType: String) {
        viewModelScope.launch {
            try {
                val contentBuilder = StringBuilder()
                resolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            contentBuilder.append(line).append("\n")
                        }
                    }
                }
                val content = contentBuilder.toString()
                if (content.isNotBlank()) {
                    val ext = Extension(
                        name = name.ifBlank { uri.lastPathSegment ?: "Imported Extension" },
                        description = description.ifBlank { "Developer mode extension" },
                        type = extensionType,
                        content = content,
                        isEnabled = true
                    )
                    repository.insertExtension(ext)
                    insertLog("LOG", "Successfully imported extension: ${ext.name}", "AppSystem", 0)
                } else {
                    insertLog("WARNING", "Extension file was empty", "AppSystem", 0)
                }
            } catch (e: Exception) {
                insertLog("ERROR", "Failed to import extension file: ${e.localizedMessage}", "AppSystem", 0)
            }
        }
    }

    fun addManualExtension(name: String, description: String, type: String, content: String) {
        viewModelScope.launch {
            val ext = Extension(
                name = name.ifBlank { "Custom Script" },
                description = description.ifBlank { "Developer written script" },
                type = type,
                content = content,
                isEnabled = true
            )
            repository.insertExtension(ext)
            insertLog("LOG", "Successfully added manual extension: ${ext.name}", "AppSystem", 0)
        }
    }

    fun toggleExtension(id: Int, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.updateExtensionStatus(id, isEnabled)
            insertLog("LOG", "Extension status changed: ID $id enabled=$isEnabled", "AppSystem", 0)
        }
    }

    fun deleteExtension(id: Int) {
        viewModelScope.launch {
            repository.deleteExtensionById(id)
            insertLog("LOG", "Extension deleted: ID $id", "AppSystem", 0)
        }
    }

    fun insertLog(level: String, message: String, sourceId: String, lineNumber: Int) {
        viewModelScope.launch {
            repository.insertLog(level, message, sourceId, lineNumber)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun insertHistoryItem(title: String, url: String) {
        viewModelScope.launch {
            if (url.isNotBlank() && !url.startsWith("data:") && !url.startsWith("blob:")) {
                repository.insertHistory(HistoryItem(title = title.ifBlank { url }, url = url))
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearAllData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearHistory()
            repository.clearLogs()
            repository.clearAllExtensions()
            repository.saveSettings(BrowserSettings())
            try {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
                android.webkit.WebStorage.getInstance().deleteAllData()
            } catch (e: Exception) {
                // Ignore webkit errors
            }
            onComplete()
        }
    }
}

class BrowserViewModelFactory(
    private val application: Application,
    private val repository: BrowserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrowserViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BrowserViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
