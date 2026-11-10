package com.coursetable.app.ui

import android.os.Build

internal data class LatinSection<T>(val letter: Char, val items: List<T>)

/** Group by the first A–Z letter of [latinOf]. Names without a letter go under '#', after Z. */
internal fun <T> groupByLatin(
    items: List<T>,
    name: (T) -> String,
    latinOf: (String) -> String
): List<LatinSection<T>> {
    val prepared = items.map { item ->
        val label = name(item)
        val latin = latinOf(label).trim()
        val letter = latin.firstOrNull { it.isLetter() }?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
        Triple(letter, latin.lowercase(), item)
    }
    return prepared.groupBy { it.first }
        .entries
        .sortedBy { if (it.key == '#') Char.MAX_VALUE else it.key }
        .map { (letter, rows) -> LatinSection(letter, rows.sortedBy { it.second }.map { it.third }) }
}

/** Lazy-list index of each section header. A header occupies one row, then its items. */
internal fun <T> latinSectionStarts(sections: List<LatinSection<T>>): Map<Char, Int> {
    var cursor = 0
    return sections.associate { section ->
        val start = cursor
        cursor += 1 + section.items.size
        section.letter to start
    }
}

private val schoolTransliterator: android.icu.text.Transliterator? by lazy {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        null
    } else {
        runCatching { android.icu.text.Transliterator.getInstance("Han-Latin/Names; Latin-ASCII") }.getOrNull()
    }
}

internal fun schoolLatin(name: String): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return name
    return schoolTransliterator?.transliterate(name) ?: name
}
