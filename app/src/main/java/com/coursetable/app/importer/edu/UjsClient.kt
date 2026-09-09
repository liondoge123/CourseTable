package com.coursetable.app.importer.edu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object UjsClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private const val BASE = "http://jwxt.ujs.edu.cn"

    suspend fun fetchSchedule(cookies: String, xnm: String?, xqm: String?): String {
        val year = xnm ?: "2025"
        val semester = xqm ?: "12"
        // 先按正方新版 GET 抓取
        runCatching { fetchGet(cookies, year, semester) }.getOrNull()?.let { return it }
        // GET 失败再按旧版 POST 兜底
        return fetchPost(cookies, year, semester)
    }

    private suspend fun fetchGet(cookies: String, year: String, semester: String): String {
        val url = "$BASE/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151&xnm=$year&xqm=$semester"
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
                if (!resp.isSuccessful) throw IllegalStateException("教务接口返回 ${resp.code}")
                if (!body.trimStart().startsWith("{")) throw IllegalStateException("非 JSON 响应")
                body
            }
        }
    }

    private suspend fun fetchPost(cookies: String, year: String, semester: String): String {
        val form = FormBody.Builder()
            .add("gnmkdm", "N2151")
            .add("xnm", year)
            .add("xqm", semester)
            .add("kzlx", "ck")
            .add("xsdm", "")
            .add("kclbdm", "")
            .add("kclxdm", "")
            .build()
        val request = Request.Builder()
            .url("$BASE/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151")
            .header("Cookie", cookies)
            .header("User-Agent", MOBILE_UA)
            .header("Accept", "*/*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "$BASE/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default")
            .post(form)
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("教务接口返回 ${resp.code}：$body")
                body
            }
        }
    }
}
