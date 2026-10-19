package com.coursetable.app.importer

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Private local draft; large text never goes into the Activity saved-state Bundle. */
internal class PasteDraftFile(private val file: File) {
    fun read(): String {
        if (!file.exists()) return ""
        require(file.length() <= PasteTimetableImporter.MAX_CHARACTERS.toLong() * 4) { "草稿过大" }
        return file.readText(Charsets.UTF_8).also {
            require(it.length <= PasteTimetableImporter.MAX_CHARACTERS) { "草稿过大" }
        }
    }

    fun write(text: String) {
        require(text.length <= PasteTimetableImporter.MAX_CHARACTERS)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            temporary.outputStream().use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}
