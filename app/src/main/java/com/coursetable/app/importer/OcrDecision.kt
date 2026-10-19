package com.coursetable.app.importer

enum class OcrField { NAME, TIME }
data class OcrAlternative(val original: String, val candidate: String, val reason: String, val confidence: Float? = null, val field: OcrField? = null)
data class OcrDecision(val text: String, val alternative: OcrAlternative? = null)

object OcrDecisions {
    /** Only join OCR-inserted gaps between Chinese characters and temporal syntax. */
    fun normalize(value: String): String = StructuredTimetableParser.normalize(value)
        .replace(Regex("(?<=[\\p{IsHan}])\\s+(?=[\\p{IsHan}])"), "")
        .replace(Regex("(?<=[0-9])\\s+(?=[周节,:-])|(?<=[周节,:-])\\s+(?=[0-9])"), "")
    private fun temporal(value: String) = Regex("星期|礼拜|\\d.*[周节]|\\d+:\\d+").containsMatchIn(value)
    private fun structural(value: String): Boolean = temporal(value) &&
        value.replace(Regex("[0-9\\s星期斯礼拜周节第一二三四五六七八九十日天单双,、.():;\\[\\]/-]"), "").isEmpty()
    private fun legal(value: String): Boolean {
        val days = Regex("(?:星期|周)([0-9一二三四五六日天八九])").findAll(value).map { it.groupValues[1] }.toList()
        if (days.any { it !in listOf("1", "2", "3", "4", "5", "6", "7", "一", "二", "三", "四", "五", "六", "日", "天") }) return false
        val ranges = Regex("(\\d+)\\s*-\\s*(\\d+)\\s*([周节])").findAll(value).toList()
        if (ranges.any { val a = it.groupValues[1].toIntOrNull() ?: return false; val b = it.groupValues[2].toIntOrNull() ?: return false; a < 1 || b < a || b > if(it.groupValues[3] == "周") 60 else 30 }) return false
        val clocks = Regex("(\\d+):(\\d+)").findAll(value).toList()
        if (clocks.any { (it.groupValues[1].toIntOrNull() ?: -1) !in 0..23 || (it.groupValues[2].toIntOrNull() ?: -1) !in 0..59 }) return false
        val singles = Regex("(?<![0-9-])(\\d+)\\s*([周节])").findAll(value).toList()
        if (singles.any { (it.groupValues[1].toIntOrNull() ?: 0) !in 1..(if (it.groupValues[2] == "周") 60 else 30) }) return false
        return (days.isNotEmpty() || ranges.isNotEmpty() || clocks.isNotEmpty() || singles.isNotEmpty()) && !Regex("(?<!\\d)-\\d|(?:^|\\D)0(?:周|节)").containsMatchIn(value)
    }
    fun choose(original: String, prediction: TinyTextRecognizer.Prediction?, supportedCodePoints: Set<Int>? = null): OcrDecision {
        val primary = normalize(original)
        if (prediction == null || prediction.confidence < .8f || prediction.text.isBlank()) return OcrDecision(primary)
        val secondary = normalize(prediction.text)
        if (primary == secondary) return OcrDecision(primary)
        val time = structural(primary) && structural(secondary)
        val outsideDictionary = supportedCodePoints != null && original.codePoints().anyMatch { !Character.isWhitespace(it) && it !in supportedCodePoints }
        val selected = if (!outsideDictionary && time && !legal(primary) && legal(secondary)) secondary else primary
        return OcrDecision(selected, OcrAlternative(original, prediction.text,
            if (outsideDictionary) "原文含复核模型字典外字符，已保留原文" else if (time) "上课时间识别不一致" else "文字识别不一致", prediction.confidence))
    }
}
