package com.coursetable.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.coursetable.app.importer.PdfParseResult
import com.coursetable.app.importer.edu.KNOWN_SCHOOLS
import com.coursetable.app.importer.edu.SchoolAdapter
import com.coursetable.app.importer.edu.ZfJwglxtAdapter
import com.coursetable.app.importer.edu.ZfJwglxtConfig
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import com.coursetable.app.importer.edu.ZfJwglxtParser
import com.coursetable.app.importer.edu.PAGE_SEMESTER_SCRIPT
import com.coursetable.app.importer.edu.pageSemesterSelection
import com.coursetable.app.importer.edu.resolveImportSemester
import com.coursetable.app.importer.edu.EduImportFailure
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import androidx.compose.ui.text.font.FontWeight

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EduImportScreen(
    semesterStart: LocalDate,
    onDone: (PdfParseResult, String) -> Unit,
    onCancel: () -> Unit,
    onPaste: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var adapter by remember { mutableStateOf<SchoolAdapter?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    var customUrl by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }
    var schoolQuery by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }

    fun clearWebSession() {
        activeWebView?.let { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.clearCache(true)
            webView.removeAllViews()
            webView.destroy()
        }
        activeWebView = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    DisposableEffect(Unit) {
        onDispose { clearWebSession() }
    }

    LiquidScrollablePage(topBar = {
        LiquidPageTopBar("从教务系统导入", onCancel) {
            if (adapter == null) {
                IconButton(onClick = {
                    customError = null
                    showCustom = true
                }) { Icon(Icons.Filled.Add, contentDescription = "自定义添加") }
            }
        }
    }) { pageInsets ->
        if (adapter == null) {
            SchoolDirectory(pageInsets, schoolQuery, { schoolQuery = it }) { adapter = it }
        } else {
            val school = adapter!!
            Column(
                Modifier.fillMaxSize().padding(pageInsets.pagePadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Text(
                "请在下方完成登录，打开课表查询页并选好学年、学期，再点导入。页面上没有学期选择时，按当前课表的开学日期导入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val shownUri = currentUrl?.let(Uri::parse)
            val shownHost = shownUri?.host ?: Uri.parse(school.loginUrl).host.orEmpty()
            val cleartextPage = shownUri?.scheme.equals("http", ignoreCase = true)
            Text(
                if (cleartextPage) {
                    "当前域名：$shownHost · 明文 HTTP（会话可能被同一网络中的攻击者窃取）"
                } else {
                    "当前域名：$shownHost · HTTPS"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (cleartextPage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        activeWebView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.safeBrowsingEnabled = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                if (request == null || !request.isForMainFrame) return false
                                val target = request.url
                                val host = target.host?.lowercase().orEmpty()
                                val scheme = target.scheme?.lowercase().orEmpty()
                                val allowed = host in school.allowedHosts &&
                                    (scheme == "https" || (scheme == "http" && host in school.cleartextHosts))
                                if (!allowed) {
                                    message = "已阻止跳转到未授权域名：${host.ifBlank { "未知域名" }}"
                                }
                                return !allowed
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                currentUrl = url
                                CookieManager.getInstance().flush()
                            }
                        }
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, false)
                        val webView = this
                        cookieManager.removeAllCookies {
                            webView.post {
                                if (activeWebView === webView) webView.loadUrl(school.loginUrl)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = {
                    clearWebSession()
                    currentUrl = null
                    adapter = null
                }) { Text("重新选择学校") }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            try {
                                val cookies = CookieManager.getInstance().getCookie(school.cookieOrigin).orEmpty()
                                if (cookies.isBlank()) {
                                    message = "尚未登录，请先在页面中完成登录"
                                    return@launch
                                }
                                val webView = activeWebView
                                if (webView == null) {
                                    message = "页面尚未打开，请稍后再试"
                                    return@launch
                                }
                                val selected = withTimeoutOrNull(3_000) { webView.evaluateJs(PAGE_SEMESTER_SCRIPT) }
                                val semester = resolveImportSemester(pageSemesterSelection(selected), semesterStart)
                                val json = withContext(Dispatchers.IO) {
                                    school.fetchSchedule(cookies, semester.xnm, semester.xqm)
                                }
                                val result = withContext(Dispatchers.IO) { ZfJwglxtParser.parseScheduleJson(json) }
                                if (result.candidates.isEmpty()) {
                                    message = result.warnings.joinToString("\n").ifBlank { "未解析到课程" }
                                } else {
                                    clearWebSession()
                                    onDone(result, school.name)
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) {
                                message = EduImportFailure.message(e)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" 导入此页课表")
                    }
                }
            }
            if (message != null) {
                Text(message!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { clearWebSession(); onPaste() }, enabled = !busy) { Text("改用粘贴导入") }
            }
            }
        }
    }
    if (showCustom) {
        CustomSchoolDialog(
            url = customUrl,
            error = customError,
            onUrlChange = {
                customUrl = it
                customError = null
            },
            onConfirm = {
                val parsed = ZfJwglxtConfig.fromLoginUrl(customUrl.trim())
                if (parsed != null) {
                    showCustom = false
                    adapter = ZfJwglxtAdapter(parsed)
                } else {
                    customError = "只支持 HTTPS 的正方教务登录页，请检查地址"
                }
            },
            onPaste = {
                showCustom = false
                onPaste()
            },
            onDismiss = { showCustom = false }
        )
    }
}

private suspend fun WebView.evaluateJs(script: String): String? = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(script) { value ->
        if (continuation.isActive) continuation.resume(value)
    }
}

@Composable
private fun SchoolDirectory(
    pageInsets: PaddingValues,
    query: String,
    onQueryChange: (String) -> Unit,
    onSchool: (SchoolAdapter) -> Unit
) {
    val direction = LocalLayoutDirection.current
    val trimmed = query.trim()
    val sections = remember(trimmed) {
        groupByLatin(
            KNOWN_SCHOOLS.filter { it.name.contains(trimmed, ignoreCase = true) },
            { it.name },
            ::schoolLatin
        )
    }
    val starts = remember(sections) { latinSectionStarts(sections) }
    val listState = rememberPageLazyListState()
    val scope = rememberCoroutineScope()
    val activeLetter by remember {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex
            sections.lastOrNull { (starts[it.letter] ?: Int.MAX_VALUE) <= index }?.letter
        }
    }
    LaunchedEffect(trimmed) { listState.scrollToItem(0) }
    val start = pageInsets.calculateStartPadding(direction) + 16.dp
    val end = pageInsets.calculateEndPadding(direction) + 16.dp
    Column(Modifier.fillMaxSize().padding(top = pageInsets.calculateTopPadding())) {
        Column(
            Modifier.padding(start = start, end = end, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("搜索学校") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${sections.sumOf { it.items.size }} 所学校",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = start,
                    end = end + 28.dp,
                    top = 12.dp,
                    bottom = pageInsets.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Gap)
            ) {
                if (sections.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "未找到学校",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                sections.forEach { section ->
                    item(key = "letter-${section.letter}") { PageSectionTitle(section.letter.toString()) }
                    items(section.items, key = { it.key }) { school ->
                        SchoolRow(school.name) { onSchool(school) }
                    }
                }
            }
            if (sections.isNotEmpty()) {
                SchoolIndexBar(
                    letters = sections.map { it.letter },
                    active = activeLetter,
                    onSelect = { letter ->
                        starts[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(
                        top = 8.dp,
                        bottom = pageInsets.calculateBottomPadding() + 8.dp,
                        end = pageInsets.calculateEndPadding(direction)
                    )
                )
            }
        }
    }
}

@Composable
private fun SchoolIndexBar(
    letters: List<Char>,
    active: Char?,
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.width(28.dp).fillMaxHeight().pointerInput(letters) {
            fun letterAt(y: Float): Char? {
                if (letters.isEmpty() || size.height <= 0) return null
                val index = (y / size.height * letters.size).toInt().coerceIn(letters.indices)
                return letters[index]
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                letterAt(down.position.y)?.let(onSelect)
                drag(down.id) { change ->
                    letterAt(change.position.y)?.let(onSelect)
                    change.consume()
                }
            }
        }
    ) {
        Column(
            Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { letter ->
                Text(
                    letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (letter == active) FontWeight.Bold else FontWeight.Medium,
                    color = if (letter == active) colors.primary else colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomSchoolDialog(
    url: String,
    error: String?,
    onUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onPaste: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义添加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "粘贴学校教务登录页地址（正方 jwglxt）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("登录页 URL") },
                    showUnfocusedBorder = false,
                    containerAlpha = 0.55f,
                    placeholder = { Text("https://jwxt.xxx.edu.cn/jwglxt/xtgl/login_slogin.html") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onPaste) { Text("改用粘贴导入") }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = url.isNotBlank()) { Text("登录") }
        },
        dismissButton = {
            val dismissController = LocalDialogDismissController.current
            TextButton(onClick = { dismissController?.dismiss() ?: onDismiss() }) { Text("取消") }
        }
    )
}

@Composable
private fun SchoolRow(name: String, onClick: () -> Unit) {
    ListItemSurface(
        material = ListItemMaterial.Page,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            ListItemChevron()
        }
    }
}
