package com.coursetable.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleaseCheckTest {
    @Test
    fun parsesReleaseTagsAndPreviewSuffixes() {
        assertEquals(SemanticVersion(1, 10, 0), GithubReleaseCheck.parseVersion("v1.10.0"))
        assertEquals(SemanticVersion(1, 10, 0), GithubReleaseCheck.parseVersion("1.10.0-preview"))
        assertNull(GithubReleaseCheck.parseVersion("preview"))
    }

    @Test
    fun reportsAnUpdateOnlyWhenTheReleaseIsNewer() {
        val newer = GithubReleaseCheck.interpret("1.9.1", "v1.10.0", "https://github.com/liondoge123/CourseTable/releases/tag/v1.10.0")
        assertEquals(
            UpdateCheckResult.UpdateAvailable("1.10.0", "https://github.com/liondoge123/CourseTable/releases/tag/v1.10.0"),
            newer,
        )
        assertEquals(UpdateCheckResult.UpToDate, GithubReleaseCheck.interpret("1.10.0", "v1.10.0", null))
        assertEquals(UpdateCheckResult.UpToDate, GithubReleaseCheck.interpret("1.10.0-preview", "v1.9.2", null))
        assertEquals(UpdateCheckResult.UpToDate, GithubReleaseCheck.interpret("1.10.0-preview", "v1.10.0", null))
    }

    @Test
    fun ignoresReleaseLinksOutsideThisRepository() {
        val result = GithubReleaseCheck.interpret("1.0.0", "v1.1.0", "https://example.com/install")
        assertEquals(
            UpdateCheckResult.UpdateAvailable("1.1.0", GithubReleaseCheck.RELEASES_URL),
            result,
        )
    }

    @Test
    fun readsTheLatestReleaseTagAndPage() {
        val body = """{"tag_name":"v1.10.0","html_url":"https://github.com/liondoge123/CourseTable/releases/tag/v1.10.0"}"""
        val release = GithubReleaseCheck.parseRelease(body)
        assertEquals("v1.10.0" to "https://github.com/liondoge123/CourseTable/releases/tag/v1.10.0", release)
        assertTrue(GithubReleaseCheck.parseRelease("not-json") == null)
        assertTrue(GithubReleaseCheck.parseRelease("{}") == null)
    }
}
