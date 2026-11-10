package com.coursetable.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolIndexTest {
    @Test fun groupsByFirstLatinLetterAndSortsWithinTheLetter() {
        val names = listOf("北京外国语大学", "安徽建筑大学", "保定学院", "123学院")
        val latin = mapOf(
            "北京外国语大学" to "Beijing Waiguoyu Daxue",
            "安徽建筑大学" to "Anhui Jianzhu Daxue",
            "保定学院" to "Baoding Xueyuan",
            "123学院" to "123"
        )
        val sections = groupByLatin(names, { it }, { latin.getValue(it) })
        assertEquals(listOf('A', 'B', '#'), sections.map { it.letter })
        assertEquals(listOf("保定学院", "北京外国语大学"), sections[1].items)
        assertEquals(mapOf('A' to 0, 'B' to 2, '#' to 5), latinSectionStarts(sections))
    }
}