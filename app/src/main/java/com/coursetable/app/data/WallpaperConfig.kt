package com.coursetable.app.data

import org.json.JSONObject

enum class WallpaperTextMode(val key: String, val label: String) {
    AUTO("auto", "自动"), DARK("dark", "深色"), LIGHT("light", "浅色");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** Positions describe the fraction of the image's overflow cropped at the leading edge. */
data class WallpaperConfig(
    val imageId: String,
    val positionX: Float = .5f,
    val positionY: Float = .5f,
    val scale: Float = 1f,
    val blurDp: Float = 8f,
    val mask: Float = .3f,
    val gridOpacity: Float = .65f,
    val cardOpacity: Float = 1f,
    val textMode: WallpaperTextMode = WallpaperTextMode.AUTO,
    val textClarity: Boolean = true
) {
    fun normalized() = copy(
        positionX = positionX.finiteOr(.5f).coerceIn(0f, 1f),
        positionY = positionY.finiteOr(.5f).coerceIn(0f, 1f),
        scale = scale.finiteOr(1f).coerceIn(1f, 3f),
        blurDp = blurDp.finiteOr(8f).coerceIn(0f, 24f),
        mask = mask.finiteOr(.3f).coerceIn(0f, .8f),
        gridOpacity = gridOpacity.finiteOr(.65f).coerceIn(0f, 1f),
        cardOpacity = cardOpacity.finiteOr(1f).coerceIn(0f, 1f)
    )

    fun encode(): String {
        require(validId(imageId))
        val c = normalized()
        return JSONObject().put("imageId", c.imageId).put("x", c.positionX)
            .put("y", c.positionY).put("scale", c.scale).put("blur", c.blurDp)
            .put("mask", c.mask).put("gridOpacity", c.gridOpacity).put("cardOpacity", c.cardOpacity)
            .put("textMode", c.textMode.key).put("textClarity", c.textClarity).toString()
    }

    companion object {
        fun validId(id: String) = id.matches(Regex("[a-f0-9-]{36}\\.png"))
        fun decode(raw: String?): WallpaperConfig? = runCatching {
            val json = JSONObject(raw ?: return null)
            val id = json.getString("imageId")
            if (!validId(id)) return null
            WallpaperConfig(id, json.optDouble("x", .5).toFloat(), json.optDouble("y", .5).toFloat(),
                json.optDouble("scale", 1.0).toFloat(), json.optDouble("blur", 8.0).toFloat(),
                json.optDouble("mask", .3).toFloat(), json.optDouble("gridOpacity", .65).toFloat(),
                json.optDouble("cardOpacity", 1.0).toFloat(),
                WallpaperTextMode.fromKey(json.optString("textMode")), json.optBoolean("textClarity", true)).normalized()
        }.getOrNull()
    }
}

private fun Float.finiteOr(fallback: Float) = if (isFinite()) this else fallback
