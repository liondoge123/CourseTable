package com.coursetable.app.importer.edu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolSecurityTest {

    @Test
    fun customSchoolRequiresHttps() {
        assertNull(ZfJwglxtConfig.fromLoginUrl("http://jwxt.example.edu/jwglxt/xtgl/login_slogin.html"))
        val config = ZfJwglxtConfig.fromLoginUrl("https://jwxt.example.edu/jwglxt/xtgl/login_slogin.html")
        assertEquals("https://jwxt.example.edu", config?.baseUrl)
    }

    @Test
    fun ujsStartsAtHttpsCasAndScopesCleartext() {
        val adapter = UjsAdapter()
        assertTrue(adapter.loginUrl.startsWith("https://pass.ujs.edu.cn/"))
        assertEquals("http://jwxt.ujs.edu.cn", adapter.cookieOrigin)
        assertEquals(setOf("jwxt.ujs.edu.cn"), adapter.cleartextHosts)
    }
}
