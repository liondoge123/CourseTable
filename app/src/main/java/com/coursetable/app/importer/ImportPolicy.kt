package com.coursetable.app.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

enum class DetectedImportType(val maxBytes: Long) {
    ICS(5L * 1024 * 1024),
    JSON(5L * 1024 * 1024),
    CSV(5L * 1024 * 1024),
    XLSX(20L * 1024 * 1024),
    PDF(30L * 1024 * 1024),
    IMAGE(25L * 1024 * 1024)
}

class ImportRejectedException(message: String) : IllegalArgumentException(message)

object ImportPolicy {
    const val MAX_PDF_PAGES = 20
    const val MAX_RENDER_PIXELS = 16_000_000L
    const val MAX_IMAGE_SOURCE_PIXELS = 100_000_000L
    const val MAX_IMAGE_DIMENSION = 32_768
    const val MAX_OUTPUT_COURSES = 2_000
    const val MAX_TIMETABLES = 20
    const val MAX_TEXT_FIELD = 256

    fun requireExternalContentUri(uri: Uri) {
        if (uri.scheme != "content") {
            throw ImportRejectedException("只允许导入通过系统文件选择器授权的文件")
        }
    }

    fun detect(prefix: ByteArray, mimeType: String?, displayName: String): DetectedImportType {
        val mime = mimeType.orEmpty().substringBefore(';').lowercase()
        val name = displayName.lowercase()
        if (prefix.startsWith("%PDF-".toByteArray())) return DetectedImportType.PDF
        val zip = prefix.size >= 4 && prefix[0] == 0x50.toByte() && prefix[1] == 0x4B.toByte() &&
            ((prefix[2] == 0x03.toByte() && prefix[3] == 0x04.toByte()) ||
                (prefix[2] == 0x05.toByte() && prefix[3] == 0x06.toByte()) ||
                (prefix[2] == 0x07.toByte() && prefix[3] == 0x08.toByte()))
        if (zip) return DetectedImportType.XLSX
        if (isImage(prefix)) return DetectedImportType.IMAGE

        val text = prefix.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (text.startsWith("BEGIN:VCALENDAR", ignoreCase = true) ||
            text.startsWith("BEGIN:VEVENT", ignoreCase = true)
        ) return DetectedImportType.ICS
        if (text.startsWith("{") || text.startsWith("[")) return DetectedImportType.JSON
        if (mime == "text/calendar" || mime == "text/vcs" || name.endsWith(".ics")) {
            throw ImportRejectedException("文件内容不是有效的日历格式")
        }
        if (mime == "application/pdf" || name.endsWith(".pdf")) {
            throw ImportRejectedException("文件内容不是有效的 PDF")
        }
        if (mime.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            throw ImportRejectedException("文件内容不是支持的图片格式")
        }
        if (mime.contains("spreadsheet") || mime == "application/vnd.ms-excel" || name.endsWith(".xlsx")) {
            throw ImportRejectedException("文件内容不是有效的 XLSX；旧版 XLS 请先另存为 XLSX 或 CSV")
        }
        if (mime == "application/json" || name.endsWith(".json")) {
            throw ImportRejectedException("文件内容不是有效的 JSON")
        }
        if (mime.startsWith("text/") || name.endsWith(".csv")) return DetectedImportType.CSV
        throw ImportRejectedException("不支持的文件类型")
    }

    fun sniff(context: Context, uri: Uri, mimeType: String?): DetectedImportType {
        val prefix = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(512)
            val count = input.read(buffer)
            if (count <= 0) ByteArray(0) else buffer.copyOf(count)
        } ?: throw ImportRejectedException("无法读取文件")
        if (prefix.isEmpty()) throw ImportRejectedException("文件为空")
        return detect(prefix, mimeType ?: context.contentResolver.getType(uri), uri.lastPathSegment.orEmpty())
    }

    fun readBytes(context: Context, uri: Uri, type: DetectedImportType): ByteArray {
        rejectKnownOversize(context, uri, type.maxBytes)
        return context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(type.maxBytes) }
            ?: throw ImportRejectedException("无法读取文件")
    }

    fun readText(context: Context, uri: Uri, type: DetectedImportType): String =
        readBytes(context, uri, type).toString(Charsets.UTF_8)

    fun copyToFile(context: Context, uri: Uri, destination: File, type: DetectedImportType) {
        rejectKnownOversize(context, uri, type.maxBytes)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyLimitedTo(output, type.maxBytes) }
        } ?: throw ImportRejectedException("无法读取文件")
    }

    fun InputStream.readBytesLimited(maxBytes: Long): ByteArray {
        val initial = minOf(maxBytes, 32 * 1024L).toInt()
        val output = ByteArrayOutputStream(initial)
        copyLimitedTo(output, maxBytes)
        return output.toByteArray()
    }

    fun InputStream.copyLimitedTo(output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) return total
            total += read
            if (total > maxBytes) throw ImportRejectedException("文件过大，无法安全导入")
            output.write(buffer, 0, read)
        }
    }

    private fun rejectKnownOversize(context: Context, uri: Uri, maxBytes: Long) {
        val size = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
        if (size != null && size > maxBytes) throw ImportRejectedException("文件过大，无法安全导入")
    }

    private fun ByteArray.startsWith(signature: ByteArray): Boolean =
        size >= signature.size && signature.indices.all { this[it] == signature[it] }

    private fun isImage(bytes: ByteArray): Boolean {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val jpeg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val webp = bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
        return bytes.startsWith(png) || jpeg || webp
    }
}
