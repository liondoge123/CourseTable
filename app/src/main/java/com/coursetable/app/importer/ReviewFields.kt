package com.coursetable.app.importer

enum class ReviewField(val prompt: String) {
    NAME("请确认课程名称"), TEACHER("请确认教师"), LOCATION("请确认上课地点"),
    DAY_OF_WEEK("请确认星期"), SECTIONS("请确认节次"), WEEKS("请确认实际上课周次"),
    TIME("请确认上课时间和地点"), GENERAL("请对照原图核对课程信息")
}

internal fun CandidateCourse.pendingReviewFields(): Set<ReviewField> =
    reviewFields ?: if (needsReview) setOf(ReviewField.GENERAL) else emptySet()

internal fun Iterable<OcrAlternative>.pendingReviewFields(): Set<ReviewField> = map {
    when (it.field) { OcrField.NAME -> ReviewField.NAME; OcrField.TIME -> ReviewField.TIME; null -> ReviewField.GENERAL }
}.toSet()

fun CandidateCourse.reviewPrompts(): List<String> = if (needsReview) pendingReviewFields().map { it.prompt } else emptyList()
