package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.scheduledWeeks

fun CandidateCourse.fieldErrors(settings: AppSettings): List<String> = buildList {
    if (name.isBlank()) add("请补齐课程名称")
    if (name.length > ImportPolicy.MAX_TEXT_FIELD) add("课程名称过长")
    if (teacher.length > ImportPolicy.MAX_TEXT_FIELD) add("教师名称过长")
    if (location.length > ImportPolicy.MAX_TEXT_FIELD) add("上课地点过长")
    if (dayOfWeek !in 1..7) add("请确认星期")
    if (startSection < 1 || duration < 1 || startSection.toLong() + duration - 1 > settings.periods.size) add("请确认节次")
    if (startWeek !in 1..settings.totalWeeks || endWeek !in startWeek..settings.totalWeeks) add("请确认周次范围")
    if (weekType !in 0..2) add("请确认单双周")
    if (asCourse().scheduledWeeks().isEmpty()) add("请至少选择一个上课周次")
    if (selectedWeeksCsv.isNotBlank() && selectedWeeksCsv.split(',').any { it.trim().toIntOrNull()?.let { week -> week !in 1..settings.totalWeeks } != false }) add("请确认选择的周次")
}

fun CandidateCourse.sourceRegionIds(): Set<String> = sourceRegions + listOfNotNull(sourceRegion) + sourceRecords.flatMap { it.sourceRegionIds() }

fun CandidateCourse.reviewIssues(settings: AppSettings): List<String> = (fieldErrors(settings) + issues + reviewPrompts()).distinct()
