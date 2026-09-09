package com.coursetable.app.importer.edu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ZfJwglxtClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun fetchSchedule(
        config: ZfJwglxtConfig,
        cookies: String,
        xnm: String? = null,
        xqm: String? = null
    ): String {
        val base = config.baseUrl.trimEnd('/') + if (config.contextPath.isEmpty()) "" else config.contextPath
        val url = buildString {
            append(base)
            append("/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151")
            if (!xnm.isNullOrBlank()) append("&xnm=").append(xnm)
            if (!xqm.isNullOrBlank()) append("&xqm=").append(xqm)
        }
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookies)
            .header("User-Agent", MOBILE_UA)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("教务接口返回 ${resp.code}：$body")
                }
                body
            }
        }
    }
}
