package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.data.Extension

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    targetUrl: String,
    isUrlLocked: Boolean,
    enforceHttps: Boolean,
    isDeveloperModeEnabled: Boolean,
    activeExtensions: List<Extension>,
    allExtensions: List<Extension>,
    onToggleExtension: (Extension) -> Unit,
    onDeleteExtension: (Extension) -> Unit,
    onAddExtensionClick: () -> Unit,
    isImmersiveMode: Boolean,
    onImmersiveModeChange: (Boolean) -> Unit,
    onLockClick: () -> Unit,
    isPasscodeEnabled: Boolean,
    onConsoleLog: (level: String, message: String, sourceId: String, lineNumber: Int) -> Unit,
    onBlockedAttempt: (url: String) -> Unit,
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    // Normalize URL
    val initialUrl = remember(targetUrl) {
        val normalized = if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            "https://$targetUrl"
        } else {
            targetUrl
        }
        if (enforceHttps && normalized.startsWith("http://")) {
            normalized.replaceFirst("http://", "https://")
        } else {
            normalized
        }
    }

    // Tabs / Slides state
    val tabs = remember { mutableStateListOf<TabData>() }
    var activeTabId by remember { mutableStateOf("") }
    val webViewCache = remember { mutableMapOf<String, WebView>() }

    if (tabs.isEmpty()) {
        val defaultTab = TabData(id = java.util.UUID.randomUUID().toString(), title = "Home", url = initialUrl)
        tabs.add(defaultTab)
        activeTabId = defaultTab.id
    }

    var currentUrl by remember { mutableStateOf(initialUrl) }

    // Sync currentUrl when switching activeTabId
    LaunchedEffect(activeTabId) {
        val tab = tabs.find { it.id == activeTabId }
        if (tab != null) {
            currentUrl = tab.url
        }
    }

    var canGoBackActive by remember { mutableStateOf(false) }
    var canGoForwardActive by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }

    // Toolbar collapse/expand state
    var isToolbarVisible by remember { mutableStateOf(true) }
    // Extensions side-panel visibility
    var isSidebarVisible by remember { mutableStateOf(false) }
    // Extension viewer detail dialog state
    var activeViewingExtension by remember { mutableStateOf<Extension?>(null) }

    // Handlers for physical back key
    val activeWebView = webViewCache[activeTabId]
    BackHandler(enabled = canGoBackActive) {
        activeWebView?.goBack()
    }

    var showUrlInputDialog by remember { mutableStateOf(false) }
    var tempUrlInput by remember { mutableStateOf("") }

    if (showUrlInputDialog) {
        AlertDialog(
            onDismissRequest = { showUrlInputDialog = false },
            title = { Text("Navigate to URL") },
            text = {
                OutlinedTextField(
                    value = tempUrlInput,
                    onValueChange = { tempUrlInput = it },
                    label = { Text("Enter Website URL") },
                    placeholder = { Text("e.g. google.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("address_dialog_input"),
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUrlInputDialog = false
                        if (tempUrlInput.isNotBlank()) {
                            val target = if (!tempUrlInput.startsWith("http://") && !tempUrlInput.startsWith("https://")) {
                                "https://$tempUrlInput"
                            } else {
                                tempUrlInput
                            }
                            currentUrl = target
                            val currentActiveView = webViewCache[activeTabId]
                            currentActiveView?.loadUrl(target)
                        }
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Browser Collapsible Toolbar
            AnimatedVisibility(
                visible = isToolbarVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00D2FF).copy(alpha = 0.5f), // Neon Blue
                                Color(0xFF00FF87).copy(alpha = 0.2f), // Neon Green
                                Color(0xFF7B2CBF).copy(alpha = 0.4f)  // Cyber Violet
                            )
                        )
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { activeWebView?.goBack() },
                                enabled = canGoBackActive
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }

                            IconButton(
                                onClick = { activeWebView?.goForward() },
                                enabled = canGoForwardActive
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Forward"
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (isLoading) {
                                        activeWebView?.stopLoading()
                                    } else {
                                        activeWebView?.reload()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                    contentDescription = if (isLoading) "Stop" else "Reload"
                                )
                            }

                            // COLLAPSE / HIDE TOOLBAR BUTTON
                            IconButton(
                                onClick = {
                                    isToolbarVisible = false
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = "Hide Toolbar"
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // Address Display Bar
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clickable(enabled = !isUrlLocked) {
                                        tempUrlInput = currentUrl
                                        showUrlInputDialog = true
                                    },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isUrlLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Lock Status",
                                        tint = if (isUrlLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentUrl.substringAfter("://"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // EXTENSION SIDEBAR TOGGLE
                            IconButton(
                                onClick = { isSidebarVisible = !isSidebarVisible }
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (allExtensions.isNotEmpty()) {
                                            Badge {
                                                Text(allExtensions.filter { it.isEnabled }.size.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Extension,
                                        contentDescription = "Extensions Panel"
                                    )
                                }
                            }

                            // PASSCODE QUICK LOCK
                            if (isPasscodeEnabled) {
                                IconButton(onClick = onLockClick) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Lock Browser",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Horizontal Tab/Slide bar
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(end = 8.dp)
                            ) {
                                items(tabs) { tab ->
                                    val isSelected = tab.id == activeTabId
                                    InputChip(
                                        selected = isSelected,
                                        onClick = { activeTabId = tab.id },
                                        label = {
                                            Text(
                                                text = tab.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                                modifier = Modifier.widthIn(max = 100.dp)
                                            )
                                        },
                                        trailingIcon = {
                                            if (tabs.size > 1) {
                                                IconButton(
                                                    onClick = {
                                                        val closedIndex = tabs.indexOfFirst { it.id == tab.id }
                                                        if (closedIndex != -1) {
                                                            tabs.removeAt(closedIndex)
                                                            webViewCache.remove(tab.id)?.destroy()
                                                            if (isSelected && tabs.isNotEmpty()) {
                                                                activeTabId = tabs[Math.max(0, closedIndex - 1)].id
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(16.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Close tab",
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.testTag("tab_chip_${tab.id}")
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val newTab = TabData(id = java.util.UUID.randomUUID().toString(), title = "New Tab", url = initialUrl)
                                    tabs.add(newTab)
                                    activeTabId = newTab.id
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Slide",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (isLoading) {
                            LinearProgressIndicator(
                                progress = { loadProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        }
                    }
                }
            }

            // WebView Holder
            val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()

            key(activeTab.id) {
                AndroidView(
                    factory = { context ->
                        webViewCache.getOrPut(activeTab.id) {
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    mediaPlaybackRequiresUserGesture = false
                                    mixedContentMode = if (enforceHttps) {
                                        WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    } else {
                                        WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    }

                                    // Remove WebView indicators to bypass embedded browser detection and look genuine
                                    val currentUA = userAgentString
                                    if (currentUA != null) {
                                        userAgentString = currentUA
                                            .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
                                            .replace("; wv", "")
                                    } else {
                                        userAgentString = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                                    }
                                }

                                // Enable cookie persistence explicitly
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        if (activeTabId == activeTab.id) {
                                            loadProgress = newProgress / 100f
                                        }
                                        super.onProgressChanged(view, newProgress)
                                    }

                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        if (consoleMessage != null && isDeveloperModeEnabled) {
                                            val lvl = when (consoleMessage.messageLevel()) {
                                                ConsoleMessage.MessageLevel.LOG -> "LOG"
                                                ConsoleMessage.MessageLevel.WARNING -> "WARNING"
                                                ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                                                ConsoleMessage.MessageLevel.DEBUG -> "DEBUG"
                                                ConsoleMessage.MessageLevel.TIP -> "LOG"
                                                else -> "LOG"
                                            }
                                            onConsoleLog(
                                                lvl,
                                                consoleMessage.message() ?: "",
                                                consoleMessage.sourceId() ?: "WebPage",
                                                consoleMessage.lineNumber()
                                            )
                                        }
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        if (activeTabId == activeTab.id) {
                                            isLoading = true
                                            url?.let { currentUrl = it }
                                        }
                                        url?.let {
                                            val idx = tabs.indexOfFirst { t -> t.id == activeTab.id }
                                            if (idx != -1) {
                                                tabs[idx] = tabs[idx].copy(url = it)
                                            }
                                        }
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        if (activeTabId == activeTab.id) {
                                            isLoading = false
                                            url?.let { currentUrl = it }
                                            canGoBackActive = canGoBack()
                                            canGoForwardActive = canGoForward()
                                        }
                                        url?.let {
                                            val idx = tabs.indexOfFirst { t -> t.id == activeTab.id }
                                            if (idx != -1) {
                                                tabs[idx] = tabs[idx].copy(
                                                    url = it,
                                                    title = view?.title ?: tabs[idx].title
                                                )
                                            }
                                            viewModel.insertHistoryItem(view?.title ?: "", it)
                                        }

                                        // Persist and flush cookies to disk
                                        CookieManager.getInstance().flush()

                                        // Inject developer mode extensions
                                        if (isDeveloperModeEnabled && url != null) {
                                            activeExtensions.forEach { extension ->
                                                if (extension.type == "js") {
                                                    evaluateJavascript(extension.content, null)
                                                } else if (extension.type == "css") {
                                                    val base64Css = Base64.encodeToString(
                                                        extension.content.toByteArray(),
                                                        Base64.NO_WRAP
                                                    )
                                                    val cssInject = """
                                                        (function() {
                                                            var parent = document.head || document.documentElement;
                                                            var style = document.getElementById('injected-css-${extension.id}');
                                                            if (!style) {
                                                                style = document.createElement('style');
                                                                style.id = 'injected-css-${extension.id}';
                                                                style.type = 'text/css';
                                                                parent.appendChild(style);
                                                            }
                                                            style.textContent = atob('$base64Css');
                                                        })();
                                                    """.trimIndent()
                                                    evaluateJavascript(cssInject, null)
                                                }
                                            }
                                        }
                                        super.onPageFinished(view, url)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val uri = request?.url ?: return false
                                        val host = uri.host ?: return false
                                        
                                        val targetUri = Uri.parse(initialUrl)
                                        val targetHost = targetUri.host ?: ""

                                        if (isUrlLocked && !host.endsWith(targetHost) && !targetHost.endsWith(host)) {
                                            onBlockedAttempt(uri.toString())
                                            return true
                                        }
                                        return false
                                    }
                                }

                                loadUrl(activeTab.url)
                            }
                        }
                    },
                    update = { webView ->
                        // Cookies and configurations updated
                        CookieManager.getInstance().flush()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // FLOATING SHOW-BAR RESTORE HANDLE (Visible when toolbar is collapsed)
        AnimatedVisibility(
            visible = !isToolbarVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF).copy(alpha = 0.12f),
                                Color(0xFF00D2FF).copy(alpha = 0.08f)
                            )
                        )
                    )
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00D2FF).copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            radius = 200f
                        )
                    )
                    .border(
                        BorderStroke(
                            width = 1.5.dp,
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00D2FF).copy(alpha = 0.7f), // Neon Blue liquid border
                                    Color(0xFF00FF87).copy(alpha = 0.3f), // Neon Green liquid border
                                    Color(0xFF7B2CBF).copy(alpha = 0.6f)  // Cyber Violet liquid border
                                )
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable {
                        isToolbarVisible = true
                        onImmersiveModeChange(false)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Show controls",
                        tint = Color(0xFF00D2FF), // Glowing Neon Blue
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Show Toolbar",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White // Crisp white for liquid glass readability
                    )
                }
            }
        }

        // EXTENSIONS DRAWER / SIDEBAR (Slides in from the right overlay style)
        AnimatedVisibility(
            visible = isSidebarVisible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            // Semi-transparent scrim background that dismisses sidebar on click
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { isSidebarVisible = false }
            ) {
                // Sidebar panel drawer
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(310.dp)
                        .clickable(enabled = false) {}, // consume clicks
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
                    tonalElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Extensions",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            IconButton(onClick = { isSidebarVisible = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Panel")
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        // Load Unpacked Option Button (Top of Sidebar)
                        Button(
                            onClick = {
                                onAddExtensionClick()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Load Unpacked Extension")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Loaded Developer Scripts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (allExtensions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeveloperMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "No extension loaded.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Load JS or CSS unpacked script.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(allExtensions) { ext ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { activeViewingExtension = ext },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = ext.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        color = if (ext.type == "js")
                                                            MaterialTheme.colorScheme.secondaryContainer
                                                        else
                                                            MaterialTheme.colorScheme.tertiaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = ext.type.uppercase(),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    if (ext.isEnabled) {
                                                        Text(
                                                            text = "Active",
                                                            color = MaterialTheme.colorScheme.primary,
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                }
                                            }

                                            // Toggle switch
                                            Switch(
                                                checked = ext.isEnabled,
                                                onCheckedChange = { onToggleExtension(ext) },
                                                thumbContent = {
                                                    Icon(
                                                        imageVector = if (ext.isEnabled) Icons.Default.Check else Icons.Default.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                                    )
                                                }
                                            )

                                            Spacer(modifier = Modifier.width(4.dp))

                                            // Delete icon
                                            IconButton(
                                                onClick = { onDeleteExtension(ext) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete script",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Reload web tab button
                        Button(
                            onClick = {
                                activeWebView?.reload()
                                isSidebarVisible = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apply & Reload Page")
                        }
                    }
                }
            }
        }

        // EXTENSION RUNNER/EDITOR DIALOG
        if (activeViewingExtension != null) {
            val ext = activeViewingExtension!!
            AlertDialog(
                onDismissRequest = { activeViewingExtension = null },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = ext.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (ext.type == "js") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = ext.type.uppercase(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (ext.isEnabled) "Status: Active" else "Status: Inactive",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ext.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Script Source Code:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Scrollable script viewer
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E1E1E),
                            border = BorderStroke(1.dp, Color(0xFF333333))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item {
                                        Text(
                                            text = ext.content,
                                            color = Color(0xFF00FF00),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Running live executes the script in the active WebView instance.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (ext.type == "js") {
                                    activeWebView?.evaluateJavascript(ext.content, null)
                                } else if (ext.type == "css") {
                                    val base64Css = Base64.encodeToString(
                                        ext.content.toByteArray(),
                                        Base64.NO_WRAP
                                    )
                                    val cssInject = """
                                        (function() {
                                            var parent = document.head || document.documentElement;
                                            var style = document.getElementById('injected-css-${ext.id}');
                                            if (!style) {
                                                style = document.createElement('style');
                                                style.id = 'injected-css-${ext.id}';
                                                style.type = 'text/css';
                                                parent.appendChild(style);
                                            }
                                            style.textContent = atob('$base64Css');
                                        })();
                                    """.trimIndent()
                                    activeWebView?.evaluateJavascript(cssInject, null)
                                }
                                activeViewingExtension = null
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Inject Live")
                        }
                        Button(onClick = { activeViewingExtension = null }) {
                            Text("Close")
                        }
                    }
                }
            )
        }
    }
}

data class TabData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "Home",
    val url: String
)
