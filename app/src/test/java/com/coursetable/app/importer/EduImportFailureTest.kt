package com.coursetable.app.importer

import com.coursetable.app.importer.edu.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EduImportFailureTest {
    @Test fun semesterAlwaysComesFromTimetableAndMissingParametersFail() {
        assertEquals(Semester("2026","3"),deriveSemester(LocalDate.of(2026,9,1)))
        assertEquals(Semester("2025","12"),deriveSemester(LocalDate.of(2026,2,23)))
        assertEquals(Semester("2026","3"),EduImportFailure.requireSemester("2026","3"))
        try { EduImportFailure.requireSemester(null,null); fail() }
        catch(e: EduImportException) { assertEquals(EduFailureKind.SEMESTER,e.kind) }
    }
    @Test fun distinguishesExpiredLoginBadPasswordChangedApiAndServerFailure() {
        listOf(Triple(401,"",EduFailureKind.LOGIN_EXPIRED),Triple(200,"<html>请先登录</html>",EduFailureKind.LOGIN_EXPIRED),
            Triple(200,"{\"msg\":\"用户名或密码不正确\"}",EduFailureKind.CREDENTIALS),Triple(200,"<html>新系统</html>",EduFailureKind.INCOMPATIBLE),
            Triple(200,"{}",EduFailureKind.INCOMPATIBLE),Triple(500,"private student data",EduFailureKind.SERVICE)).forEach { (code, body, expected) ->
            try { EduImportFailure.validateResponse(code,body); fail() }
            catch(e: EduImportException) { assertEquals(expected,e.kind); assertFalse(EduImportFailure.message(e).contains("private")) }
        }
        assertEquals("{\"kbList\":[]}",EduImportFailure.validateResponse(200,"{\"kbList\":[]}"))
        assertEquals(EduFailureKind.NETWORK,EduImportFailure.kind(java.io.IOException()))
    }
    @Test fun presetsHaveUniqueKeysAndOnlyOwnHostsAreAuthorized() {
        assertEquals(KNOWN_SCHOOLS.size,KNOWN_SCHOOLS.map { it.key }.distinct().size)
        assertTrue(KNOWN_SCHOOLS.size >= 6)
        KNOWN_SCHOOLS.filter { it.key != "ujs" }.forEach {
            val config = ZfJwglxtConfig.fromLoginUrl(it.loginUrl)!!
            assertEquals(setOf(java.net.URI(config.baseUrl).host),it.allowedHosts)
            assertTrue(it.cleartextHosts.isEmpty())
        }
    }
}
