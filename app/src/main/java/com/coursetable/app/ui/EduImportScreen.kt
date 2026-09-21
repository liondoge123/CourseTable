package com.coursetable.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.coursetable.app.importer.edu.deriveSemester
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EduImportScreen(
    semesterStart: LocalDate,
    onDone: (PdfParseResult, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adapter by remember { mutableStateOf<SchoolAdapter?>(null) }
    var customUrl by remember { mutableStateOf("") }
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

    FullscreenPageContainer {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("从教务系统导入", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        if (adapter == null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SectionFrame {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("选择学校", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KNOWN_SCHOOLS.forEach { school ->
                                SchoolButton(
                                    name = school.name,
                                    onClick = { adapter = school }
                                )
                            }
                        }
                    }
                }

                SectionFrame {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("自定义教务系统", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "或粘贴学校教务登录页地址（正方 jwglxt）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = customUrl,
                            onValueChange = { customUrl = it },
                            label = { Text("登录页 URL") },
                            placeholder = { Text("https://jwxt.xxx.edu.cn/jwglxt/xtgl/login_slogin.html") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val parsed = ZfJwglxtConfig.fromLoginUrl(customUrl.trim())
                                if (parsed != null) {
                                    adapter = ZfJwglxtAdapter(parsed)
                                } else {
                                    message = "只支持 HTTPS 的正方教务登录页，请检查地址"
                                }
                            },
                            enabled = customUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("使用此地址登录")
                        }
                        if (message != null) {
                            Text(message!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            val school = adapter!!
            Text(
                "请在下方页面完成登录（含验证码），登录成功进入个人主页后点击底部按钮",
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
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
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
                                val semester = deriveSemester(semesterStart)
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
                            } catch (e: Exception) {
                                message = "导入失败，请确认登录状态和网络后重试"
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
                        Text(" 登录完成，开始导入")
                    }
                }
            }
            if (message != null) {
                Text(message!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    }
}

@Composable
private fun SchoolButton(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.clip(shape),
        shape = shape,
        color = colors.primary.copy(alpha = 0.12f),
        contentColor = colors.primary,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary
            )
        }
    }
}
