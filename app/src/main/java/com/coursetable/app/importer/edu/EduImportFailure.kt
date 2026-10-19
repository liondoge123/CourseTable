package com.coursetable.app.importer.edu

import java.io.IOException
import org.json.JSONObject

enum class EduFailureKind { LOGIN_EXPIRED, CREDENTIALS, INCOMPATIBLE, NETWORK, SERVICE, SEMESTER }

class EduImportException(val kind: EduFailureKind) : IllegalStateException(kind.name)

object EduImportFailure {
    fun requireSemester(xnm: String?, xqm: String?): Semester {
        if (xnm == null || !Regex("\\d{4}").matches(xnm) || xqm !in listOf("3", "12", "16")) {
            throw EduImportException(EduFailureKind.SEMESTER)
        }
        return Semester(xnm, xqm!!)
    }

    /** Never surface response bodies: they may contain student information or authentication tokens. */
    fun validateResponse(status: Int, body: String): String {
        if (status == 401 || status == 403) throw EduImportException(EduFailureKind.LOGIN_EXPIRED)
        if (status !in 200..299) throw EduImportException(EduFailureKind.SERVICE)
        val text = body.trimStart()
        if (Regex("密码错误|用户名或密码|账号或密码|密码不正确|密码不对").containsMatchIn(text)) throw EduImportException(EduFailureKind.CREDENTIALS)
        if (Regex("login_slogin|cas/login|登录超时|登录失效|登录过期|请.*登录|用户未登录", RegexOption.IGNORE_CASE).containsMatchIn(text)) throw EduImportException(EduFailureKind.LOGIN_EXPIRED)
        val root = runCatching { JSONObject(text) }.getOrNull()
        if (root?.optJSONArray("kbList") == null) throw EduImportException(EduFailureKind.INCOMPATIBLE)
        return body
    }

    fun kind(error: Throwable): EduFailureKind = when (error) {
        is EduImportException -> error.kind
        is IOException -> EduFailureKind.NETWORK
        else -> EduFailureKind.SERVICE
    }

    fun message(error: Throwable): String = when (kind(error)) {
        EduFailureKind.LOGIN_EXPIRED -> "登录已失效或尚未完成，请重新登录后再导入"
        EduFailureKind.CREDENTIALS -> "教务系统提示账号或密码错误，请核对后重试，避免连续尝试导致锁号"
        EduFailureKind.INCOMPATIBLE -> "学校接口暂不兼容。可以复制教务课表后使用粘贴导入，或导入课表图片/PDF"
        EduFailureKind.NETWORK -> "无法连接教务系统，请检查网络或校园网连接后重试"
        EduFailureKind.SERVICE -> "教务系统暂时不可用，请稍后重试，也可使用粘贴导入"
        EduFailureKind.SEMESTER -> "学期参数缺失或无效，请检查当前课表的学期开始日期"
    }
}
