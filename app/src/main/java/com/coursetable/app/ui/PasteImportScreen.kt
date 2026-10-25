package com.coursetable.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.PasteTimetableImporter
import com.coursetable.app.importer.PdfParseResult
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PasteImportState(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val drafts = PasteDraftRepository.get(application)
    var text by mutableStateOf("")
        private set
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    val persistenceError = drafts.error

    init {
        viewModelScope.launch {
            text = drafts.load()
            ready = true
        }
    }

    fun updateText(value: String) {
        text = value
        error = null
        drafts.save(value)
    }
}

@Composable
internal fun PasteImportScreen(settings: AppSettings, onParsed: (PdfParseResult) -> Unit, onBack: () -> Unit) {
    val state: PasteImportState = androidx.lifecycle.viewmodel.compose.viewModel()
    val persistenceError by state.persistenceError.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    LiquidScrollablePage(topBar = { LiquidPageTopBar("粘贴导入", onBack) }) { pageInsets ->
        EndInputOnOutsideTap { outsideTap ->
            Column(Modifier.fillMaxSize().then(outsideTap).imePadding().verticalScroll(rememberPageScrollState()).padding(pageInsets.pagePadding()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("粘贴教务表格、AI 生成的课程 JSON 或同学分享的课表，解析后统一校对。",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = state.text, onValueChange = {
                    if (it.length <= PasteTimetableImporter.MAX_CHARACTERS) { state.updateText(it) }
                    else state.error = "内容过长，请分批导入"
                }, label = { Text("课表内容") }, placeholder = { Text("复制含表头的教务表格，或粘贴 {\"courses\":[…]}") },
                    enabled = state.ready && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 380.dp).testTag("paste-import-content"))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = {
                        val clip = clipboard.primaryClip
                        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString().orEmpty() else ""
                        if (text.length > PasteTimetableImporter.MAX_CHARACTERS) state.error = "剪贴板内容过长，请分批导入"
                        else { state.updateText(text); state.error = if (text.isBlank()) "剪贴板中没有文本" else null }
                    }, enabled = state.ready && !busy) { Text("粘贴剪贴板") }
                    TextButton(onClick = { state.updateText("") }, enabled = state.ready && !busy) { Text("清空") }
                }
                if (persistenceError != null) Text(persistenceError!!, color = MaterialTheme.colorScheme.error)
                if (!state.ready) Text("正在恢复草稿…")
                if (state.error != null) Text(state.error!!, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("paste-import-error"))
                Button(onClick = {
                    busy = true; state.error = null
                    val source = state.text
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.Default) { PasteTimetableImporter.parse(source, settings) }
                            onParsed(result)
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { state.error = e.message ?: "解析失败，请检查粘贴内容" }
                        finally { busy = false }
                    }
                }, enabled = state.ready && !busy && state.text.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("paste-import-parse")) {
                    Text(if (busy) "正在解析…" else "解析并校对")
                }
                HorizontalDivider()
                Text("图片识别不理想时", style = MaterialTheme.typography.titleMedium)
                Text("复制提示词，把课表图片和提示词一起发给你选择的 AI，再将生成的 JSON 粘贴到这里。图片由你自行发送。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("课表识别提示词", PasteTimetableImporter.aiPrompt(settings)))
                    copied = true
                }, modifier = Modifier.fillMaxWidth()) { Text(if (copied) "提示词已复制" else "复制 AI 识别提示词") }
            }
        }
    }
}
