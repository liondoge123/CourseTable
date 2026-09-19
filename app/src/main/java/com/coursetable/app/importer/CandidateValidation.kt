package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings

fun CandidateCourse.fieldErrors(settings: AppSettings): List<String> = buildList {
    if (name.isBlank()) add("请补齐课程名称")
    if (dayOfWeek !in 1..7) add("请确认星期")
    if (startSection < 1 || duration < 1 || startSection.toLong() + duration - 1 > settings.periods.size) add("请确认节次")
    if (startWeek !in 1..settings.totalWeeks || endWeek !in startWeek..settings.totalWeeks) add("请确认周次范围")
    if (weekType !in 0..2) add("请确认单双周")
}

fun CandidateCourse.sourceRegionIds(): Set<String> = sourceRegions + listOfNotNull(sourceRegion) + sourceRecords.flatMap { it.sourceRegionIds() }

fun CandidateCourse.reviewIssues(settings: AppSettings): List<String> = fieldErrors(settings).ifEmpty {
    if (needsReview) listOf("请对照原图核对课程信息") else emptyList()
}
