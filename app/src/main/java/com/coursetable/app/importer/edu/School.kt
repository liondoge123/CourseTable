package com.coursetable.app.importer.edu

import java.time.LocalDate

data class ZfJwglxtConfig(
    val key: String,
    val name: String,
    val baseUrl: String,
    val contextPath: String
) {
    val loginUrl: String
        get() = if (contextPath.isEmpty()) {
            "$baseUrl/xtgl/login_slogin.html"
        } else {
            "$baseUrl$contextPath/xtgl/login_slogin.html"
        }

    companion object {
        fun fromLoginUrl(url: String): ZfJwglxtConfig? {
            val trimmed = url.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
            if (uri.userInfo != null) return null
            val host = uri.host ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "https") return null
            val path = uri.path.orEmpty()
            if (!path.contains("xtgl/login_slogin.html", ignoreCase = true) && !path.contains("xtgl/login")) {
                return null
            }
            val contextPath = path.substringBefore("/xtgl/login").trimEnd('/')
            return ZfJwglxtConfig(
                key = "custom",
                name = host,
                baseUrl = "$scheme://${uri.rawAuthority}",
                contextPath = contextPath
            )
        }
    }
}

data class Semester(val xnm: String, val xqm: String)

fun deriveSemester(semesterStart: LocalDate): Semester {
    val month = semesterStart.monthValue
    return if (month in 8..12) {
        Semester(xnm = semesterStart.year.toString(), xqm = "3")
    } else {
        Semester(xnm = (semesterStart.year - 1).toString(), xqm = "12")
    }
}

interface SchoolAdapter {
    val key: String
    val name: String
    val loginUrl: String
    val cookieOrigin: String
    val allowedHosts: Set<String>
    val cleartextHosts: Set<String> get() = emptySet()
    suspend fun fetchSchedule(cookies: String, xnm: String?, xqm: String?): String
}

class ZfJwglxtAdapter(private val config: ZfJwglxtConfig) : SchoolAdapter {
    override val key: String get() = config.key
    override val name: String get() = config.name
    override val loginUrl: String get() = config.loginUrl
    override val cookieOrigin: String get() = config.baseUrl
    override val allowedHosts: Set<String> get() = setOf(java.net.URI(config.baseUrl).host.lowercase())
    override suspend fun fetchSchedule(cookies: String, xnm: String?, xqm: String?): String =
        ZfJwglxtClient.fetchSchedule(config, cookies, xnm, xqm)
}

class UjsAdapter : SchoolAdapter {
    override val key: String = "ujs"
    override val name: String = "江苏大学"
    override val loginUrl: String =
        "https://pass.ujs.edu.cn/cas/login?service=http%3A%2F%2Fjwxt.ujs.edu.cn%2Fsso%2Fjziotlogin"
    override val cookieOrigin: String = "http://jwxt.ujs.edu.cn"
    override val allowedHosts: Set<String> = setOf("pass.ujs.edu.cn", "jwxt.ujs.edu.cn")
    override val cleartextHosts: Set<String> = setOf("jwxt.ujs.edu.cn")
    override suspend fun fetchSchedule(cookies: String, xnm: String?, xqm: String?): String =
        UjsClient.fetchSchedule(cookies, xnm, xqm)
}

val KNOWN_SCHOOLS: List<SchoolAdapter> = listOf(
    UjsAdapter()
)
