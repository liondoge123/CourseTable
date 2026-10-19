package com.coursetable.app.ui

import android.content.Context
import com.coursetable.app.importer.PasteDraftFile
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Application lifetime writer: leaving the page must not cancel an accepted draft save. */
internal class PasteDraftRepository private constructor(context: Context) {
    private val file = PasteDraftFile(File(context.noBackupFilesDir, "paste-import-draft.txt"))
    private val writes = Channel<String>(Channel.CONFLATED)
    val initial = CompletableDeferred<String>()
    @Volatile private var latestText = ""
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { latestText = file.read(); initial.complete(latestText) }
            catch (_: Exception) {
                mutableError.value = "未能读取本地草稿，请重新粘贴"
                initial.complete("")
            }
            for (text in writes) {
                try { file.write(text); mutableError.value = null }
                catch (_: Exception) { mutableError.value = "草稿尚未保存，请保留原文并重试" }
            }
        }
    }

    suspend fun load(): String { initial.await(); return latestText }

    fun save(text: String) { latestText = text; writes.trySend(text) }

    companion object {
        @Volatile private var instance: PasteDraftRepository? = null
        fun get(context: Context): PasteDraftRepository = instance ?: synchronized(this) {
            instance ?: PasteDraftRepository(context.applicationContext).also { instance = it }
        }
    }
}
