package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "browser_settings")
data class BrowserSettings(
    @PrimaryKey val id: Int = 1,
    val targetUrl: String = "https://duckduckgo.com",
    val isUrlLocked: Boolean = false,
    val isPasscodeEnabled: Boolean = false,
    val passcode: String = "",
    val enforceHttps: Boolean = true,
    val clearCacheOnExit: Boolean = false,
    val isDeveloperModeEnabled: Boolean = true
)

@Entity(tableName = "extensions")
data class Extension(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val type: String, // "js" or "css"
    val content: String,
    val isEnabled: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "console_logs")
data class ConsoleLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val level: String, // "LOG", "WARNING", "ERROR"
    val message: String,
    val sourceId: String,
    val lineNumber: Int,
    val timestamp: Long = System.currentTimeMillis()
)
