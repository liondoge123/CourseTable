package com.coursetable.app.importer

import org.junit.Assert.*
import org.junit.Test

class OcrDecisionTest {
    @Test fun temporalTextInsideANameCannotAuthorizeTinyReplacement() {
        val choice = OcrDecisions.choose("课程设计1-0周", TinyTextRecognizer.Prediction("1-16周", .99f))
        assertEquals("课程设计1-0周", choice.text)
        assertNotNull(choice.alternative)
    }
    @Test fun dictionaryOutsideSymbolsProtectOriginalAndDisagreementIsMarked() {
        val dictionary = "星期一二三四五六日0123456789-周节 ".codePoints().toArray().toSet()
        val choice = OcrDecisions.choose("专业实践Ⅱ", TinyTextRecognizer.Prediction("专业实践工", .99f), dictionary)
        assertEquals("专业实践Ⅱ", choice.text)
        assertTrue(choice.alternative!!.reason.contains("字典外"))
        val repaired = OcrDecisions.choose("星期八 1-2节", TinyTextRecognizer.Prediction("星期六 1-2节", .99f))
        assertEquals(OcrDecisions.normalize("星期六 1-2节"), repaired.text)
        assertNotNull(repaired.alternative)
    }
    @Test fun differentLegalTimesAreNeverSilentlyReplaced() {
        val choice = OcrDecisions.choose("1-16周 星期一 1-2节", TinyTextRecognizer.Prediction("1-16周 星期三 1-2节", .99f))
        assertEquals(OcrDecisions.normalize("1-16周 星期一 1-2节"), choice.text)
        assertNotNull(choice.alternative)
    }
    @Test fun namesKeepAlternativesAndEnglishWordBoundaries() {
        val choice = OcrDecisions.choose("数 学", TinyTextRecognizer.Prediction("数学A", .95f))
        assertEquals("数学", choice.text); assertEquals("数学A", choice.alternative!!.candidate)
        assertEquals("Computer Science", OcrDecisions.normalize("Computer Science"))
    }
    @Test fun lowConfidenceCannotReplaceOrInventACandidate() {
        val choice = OcrDecisions.choose("数学", TinyTextRecognizer.Prediction("英语", .3f))
        assertEquals("数学", choice.text); assertNull(choice.alternative)
    }
    @Test fun missingWeeksDoNotBecomeAnEntireSemester() {
        val tokens = listOf(TextToken(200f, 900f, 250f, 920f, "星期一"), TextToken(400f, 900f, 450f, 920f, "星期二"),
            TextToken(20f, 800f, 50f, 820f, "第1节"), TextToken(200f, 800f, 250f, 820f, "高等数学"))
        val candidate = StructuredTimetableParser.extract(tokens, 600f, 1000f, com.coursetable.app.data.AppSettings()).candidates.single()
        assertEquals(0, candidate.startWeek); assertTrue(candidate.issues.any { "周次缺失" in it })
    }
}
