package com.coursetable.app.data

enum class GlassEffect(val key: String, val label: String) {
    STANDARD("standard", "标准玻璃"),
    CLEAR("clear", "通透玻璃");

    companion object {
        fun fromKey(key: String?): GlassEffect = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}
