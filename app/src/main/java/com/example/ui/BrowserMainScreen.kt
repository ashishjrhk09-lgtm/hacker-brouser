package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BrowserSettings
import com.example.data.ConsoleLog
import com.example.data.Extension
import com.example.data.HistoryItem

enum class BrowserTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Default.Home),
    EXTENSIONS("Extensions", Icons.Default.Extension),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMainScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val extensions by viewModel.extensions.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val isAuthorized by viewModel.isAuthorized.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(BrowserTab.HOME) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isImmersiveMode by remember { mutableStateOf(false) }
    var showSplash by rememberSaveable { mutableStateOf(true) }

    if (showSplash) {
        HackerSplashScreen(onFinished = { showSplash = false })
    } else if (!isAuthorized && settings.isPasscodeEnabled) {
        PasscodeLockGate(
            correctPasscode = settings.passcode,
            onUnlockSuccess = { viewModel.verifyPasscode(settings.passcode) },
            onResetAttempt = {
                Toast.makeText(context, "Passcode required to access secure environment", Toast.LENGTH_SHORT).show()
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = !isImmersiveMode || currentTab != BrowserTab.HOME,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    NavigationBar {
                        BrowserTab.values().forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = { Text(tab.title) },
                                modifier = Modifier.testTag("tab_item_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    BrowserTab.HOME -> {
                        BrowserWebView(
                            targetUrl = settings.targetUrl,
                            isUrlLocked = settings.isUrlLocked,
                            enforceHttps = settings.enforceHttps,
                            isDeveloperModeEnabled = settings.isDeveloperModeEnabled,
                            activeExtensions = extensions.filter { it.isEnabled },
                            allExtensions = extensions,
                            onToggleExtension = { ext -> viewModel.toggleExtension(ext.id, !ext.isEnabled) },
                            onDeleteExtension = { ext -> viewModel.deleteExtension(ext.id) },
                            onAddExtensionClick = { showAddDialog = true },
                            isImmersiveMode = isImmersiveMode,
                            onImmersiveModeChange = { isImmersiveMode = it },
                            onLockClick = { viewModel.lockBrowser() },
                            isPasscodeEnabled = settings.isPasscodeEnabled,
                            onConsoleLog = { level, message, source, line ->
                                viewModel.insertLog(level, message, source, line)
                            },
                            onBlockedAttempt = { url ->
                                viewModel.insertLog("WARNING", "Blocked navigation attempt to out-of-scope domain: $url", "SecurityFilter", 0)
                                Toast.makeText(context, "Browsing is locked to this domain!", Toast.LENGTH_LONG).show()
                            },
                            viewModel = viewModel
                        )
                    }
                    BrowserTab.EXTENSIONS -> {
                        ExtensionsPane(
                            extensions = extensions,
                            onToggle = { ext -> viewModel.toggleExtension(ext.id, !ext.isEnabled) },
                            onDelete = { ext -> viewModel.deleteExtension(ext.id) },
                            onAddClick = { showAddDialog = true }
                        )
                    }
                    BrowserTab.SETTINGS -> {
                        SettingsScreen(
                            settings = settings,
                            viewModel = viewModel,
                            logs = logs,
                            onSave = { updatedSettings ->
                                viewModel.updateSettings(
                                    targetUrl = updatedSettings.targetUrl,
                                    isUrlLocked = updatedSettings.isUrlLocked,
                                    isPasscodeEnabled = updatedSettings.isPasscodeEnabled,
                                    passcode = updatedSettings.passcode,
                                    enforceHttps = updatedSettings.enforceHttps,
                                    clearCacheOnExit = updatedSettings.clearCacheOnExit,
                                    isDeveloperModeEnabled = updatedSettings.isDeveloperModeEnabled
                                )
                                Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddExtensionDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name, description, type, content ->
                    viewModel.addManualExtension(name, description, type, content)
                    showAddDialog = false
                },
                onImportFile = { uri, name, description, type ->
                    viewModel.importExtensionFromFile(uri, context.contentResolver, name, description, type)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun PasscodeLockGate(
    correctPasscode: String,
    onUnlockSuccess: () -> Unit,
    onResetAttempt: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Secure Browser Locked",
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(72.dp)
                    .padding(bottom = 16.dp)
            )

            Text(
                text = "Secure Space Locked",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Please enter your developer passcode PIN to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            // PIN Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                repeat(4) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (filled) {
                                    if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    )
                }
            }

            if (isError) {
                Text(
                    text = "Incorrect passcode! Try again.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Keypad 3x4 Grid
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "⌫")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { key ->
                            IconButton(
                                onClick = {
                                    isError = false
                                    when (key) {
                                        "C" -> enteredPin = ""
                                        "⌫" -> if (enteredPin.isNotEmpty()) {
                                            enteredPin = enteredPin.dropLast(1)
                                        }
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                enteredPin += key
                                                if (enteredPin.length == 4) {
                                                    if (enteredPin == correctPasscode) {
                                                        onUnlockSuccess()
                                                    } else {
                                                        isError = true
                                                        enteredPin = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape
                                    )
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = onResetAttempt) {
                Text("Hint / Help", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun ExtensionsPane(
    extensions: List<Extension>,
    onToggle: (Extension) -> Unit,
    onDelete: (Extension) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (extensions.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = "No extensions",
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = "No Extensions Installed",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                text = "In developer mode, you can import custom .js scripts or .css stylesheets from local files or write them directly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            Button(
                onClick = onAddClick,
                modifier = Modifier.testTag("pane_add_extension_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Custom Extension")
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize()
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Developer Extensions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New")
                    }
                }
            }
            items(extensions) { extension ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (extension.isEnabled) {
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        } else {
                            MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = extension.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (extension.type == "js") {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            },
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = extension.type.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (extension.type == "js") {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        }
                                    )
                                }
                            }
                            Text(
                                text = extension.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = "Lines: ${extension.content.lines().size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Switch(
                            checked = extension.isEnabled,
                            onCheckedChange = { onToggle(extension) },
                            modifier = Modifier.testTag("extension_toggle_${extension.id}")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = { onDelete(extension) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete extension",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsolePane(
    logs: List<ConsoleLog>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Developer Log Console",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Badge { Text("${logs.size}") }
            }
            TextButton(
                onClick = onClear,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear Logs")
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = "Console is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    val color = when (log.level) {
                        "ERROR" -> MaterialTheme.colorScheme.error
                        "WARNING" -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f) // Orangeish / Redish
                        "DEBUG" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = when (log.level) {
                                    "ERROR" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    "WARNING" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "[${log.level}]",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = color,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = "source: ${log.sourceId.substringAfterLast("/")} (line ${log.lineNumber})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecuritySettingsPane(
    settings: BrowserSettings,
    onSave: (BrowserSettings) -> Unit,
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urlInput by remember { mutableStateOf(settings.targetUrl) }
    var isUrlLocked by remember { mutableStateOf(settings.isUrlLocked) }
    var isPasscodeEnabled by remember { mutableStateOf(settings.isPasscodeEnabled) }
    var passcodeVal by remember { mutableStateOf(settings.passcode) }
    var enforceHttps by remember { mutableStateOf(settings.enforceHttps) }
    var clearCacheOnExit by remember { mutableStateOf(settings.clearCacheOnExit) }
    var isDeveloperModeEnabled by remember { mutableStateOf(settings.isDeveloperModeEnabled) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Security & Developer Settings",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = "Browser Domain Restrictions",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { newValue -> urlInput = newValue },
                label = { Text("Locked / Target Site URL") },
                placeholder = { Text("e.g., github.com") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target_url_input"),
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Lock Navigation to Target Host",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                "Blocks links and redirects leading outside the target domain, enforcing single-site security.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isUrlLocked,
                            onCheckedChange = { isUrlLocked = it },
                            modifier = Modifier.testTag("lock_navigation_switch")
                        )
                    }
                }
            }
        }

        item {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Security & Privacy Shields",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enforce Secure Connections (HTTPS)",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                "Forces all pages to run over encrypted SSL/TLS layers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enforceHttps, onCheckedChange = { enforceHttps = it })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Passcode App Lock",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                "Enforce a 4-digit PIN authentication on browser startup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPasscodeEnabled,
                            onCheckedChange = { isPasscodeEnabled = it },
                            modifier = Modifier.testTag("passcode_lock_switch")
                        )
                    }

                    if (isPasscodeEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = passcodeVal,
                            onValueChange = { newValue -> if (newValue.length <= 4) passcodeVal = newValue },
                            label = { Text("4-Digit Passcode PIN") },
                            placeholder = { Text("e.g. 1234") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("passcode_pin_input")
                        )
                    }
                }
            }
        }

        item {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Developer Environment Settings",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enable Developer Extensions Engine",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                "Permits local injection of JavaScript & CSS scripts into the secure sandbox.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDeveloperModeEnabled,
                            onCheckedChange = { isDeveloperModeEnabled = it },
                            modifier = Modifier.testTag("dev_mode_switch")
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onSave(
                        BrowserSettings(
                            targetUrl = urlInput,
                            isUrlLocked = isUrlLocked,
                            isPasscodeEnabled = isPasscodeEnabled,
                            passcode = if (isPasscodeEnabled) passcodeVal else "",
                            enforceHttps = enforceHttps,
                            clearCacheOnExit = clearCacheOnExit,
                            isDeveloperModeEnabled = isDeveloperModeEnabled
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Security Preferences")
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onClearAllData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("clear_all_data_button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear All Browser Data & Cache")
            }
        }
    }
}

@Composable
fun AddExtensionDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, description: String, type: String, content: String) -> Unit,
    onImportFile: (uri: Uri, name: String, description: String, type: String) -> Unit
) {
    val context = LocalContext.current
    var isImportMode by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("js") }
    var contentText by remember { mutableStateOf("") }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedFileName by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri
            var displayName = ""
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        displayName = c.getString(index) ?: ""
                    }
                }
            }
            if (displayName.isBlank()) {
                displayName = uri.lastPathSegment ?: "script.js"
            }
            pickedFileName = displayName

            // Autofill fields
            if (name.isBlank()) {
                name = displayName.substringBeforeLast(".")
            }
            if (displayName.endsWith(".css", ignoreCase = true)) {
                type = "css"
            } else {
                type = "js"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), // Transparent Liquid Glass
            border = BorderStroke(
                width = 1.5.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF00D2FF).copy(alpha = 0.6f), // Neon Blue liquid border
                        Color(0xFF00FF87).copy(alpha = 0.2f), // Neon Green liquid border
                        Color(0xFF7B2CBF).copy(alpha = 0.5f)  // Cyber Violet liquid border
                    )
                )
            ),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00D2FF).copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            radius = 600f
                        )
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Add Developer Extension",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Dialog")
                    }
                }

                // Mode Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isImportMode) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { isImportMode = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Import File",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isImportMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (!isImportMode) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { isImportMode = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Write Manual",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (!isImportMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Extension Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ext_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Script Type Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Script Language:", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == "js", onClick = { type = "js" })
                        Text("JavaScript (.js)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == "css", onClick = { type = "css" })
                        Text("Stylesheet (.css)")
                    }
                }

                if (isImportMode) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { filePickerLauncher.launch("*/*") },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (pickedUri != null) Icons.Default.Task else Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (pickedUri != null) pickedFileName else "Select Script File from Local Device",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Supports raw text containing JS or CSS code",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = contentText,
                        onValueChange = { contentText = it },
                        label = { Text("Script Code Body") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("ext_code_input"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        placeholder = {
                            Text(
                                if (type == "js") "console.log('Hello from my extension!');" else "body { background-color: #000; }"
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isImportMode) {
                                pickedUri?.let { uri ->
                                    onImportFile(uri, name, description, type)
                                } ?: Toast.makeText(context, "Please select a script file first!", Toast.LENGTH_SHORT).show()
                            } else {
                                if (contentText.isNotBlank()) {
                                    onAdd(name, description, type, contentText)
                                } else {
                                    Toast.makeText(context, "Please enter script code first!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("dialog_submit_button")
                    ) {
                        Text("Install Extension")
                    }
                }
            }
        }
    }
}

@Composable
fun HackerSplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1800)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE6F0FA), // Ice Blue
                        Color(0xFFFFFFFF), // Pure White
                        Color(0xFFD4E6F1)  // Soft Light Navy Blue
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Elegant pulsing globe logo in deep full navy blue
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = Color(0xFF002D62), // Rich Navy Blue
                modifier = Modifier
                    .size(80.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Clean "MADE WITH ❤️ BY ASHISH" in full navy blue
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MADE WITH",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color(0xFF002D62), // Rich Navy Blue
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                )
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Love",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "BY ASHISH",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color(0xFF002D62), // Rich Navy Blue
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CircularProgressIndicator(
                color = Color(0xFF002D62), // Rich Navy Blue
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun SettingsScreen(
    settings: BrowserSettings,
    viewModel: BrowserViewModel,
    logs: List<ConsoleLog>,
    onSave: (BrowserSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selectedSubTab by remember { mutableStateOf(0) } // 0 = Security, 1 = History, 2 = Console

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedSubTab) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Security") },
                icon = { Icon(Icons.Default.Security, contentDescription = null) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("History") },
                icon = { Icon(Icons.Default.History, contentDescription = null) }
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                text = { Text("Console") },
                icon = { Icon(Icons.Default.Terminal, contentDescription = null) }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedSubTab) {
                0 -> {
                    SecuritySettingsPane(
                        settings = settings,
                        onSave = onSave,
                        onClearAllData = {
                            viewModel.clearAllData {
                                Toast.makeText(context, "All browser data & history cleared!", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
                1 -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Browsing History",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (history.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        viewModel.clearHistory()
                                        Toast.makeText(context, "History cleared!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear History")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (history.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No history recorded yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(history) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val dateStr = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.timestamp))
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    ConsolePane(logs = logs, onClear = { viewModel.clearLogs() })
                }
            }
        }
    }
}
