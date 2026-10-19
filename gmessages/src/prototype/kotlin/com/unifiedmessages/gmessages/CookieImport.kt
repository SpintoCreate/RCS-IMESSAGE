package com.unifiedmessages.gmessages

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import net.lingala.zip4j.io.inputstream.ZipInputStream
import java.io.ByteArrayOutputStream

/** Reads the documented cookie JSON from one AES-256 ZIP entry using zip4j. */
internal object CookieImport {
    private const val MAX_COOKIE_BYTES = 64 * 1024

    fun decrypt(resolver: ContentResolver, uri: Uri, passphrase: String): String {
        require(passphrase.length >= 16) { "Enter the export passphrase (at least 16 characters)." }
        resolver.openInputStream(uri).use { source ->
            requireNotNull(source) { "The selected file could not be opened." }
            ZipInputStream(source, passphrase.toCharArray()).use { archive ->
                val entry = archive.nextEntry ?: error("The encrypted ZIP is empty.")
                require(!entry.isDirectory && entry.fileName == "cookies.json") {
                    "Choose a Google Messages cookie export made by the desktop helper."
                }
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val read = archive.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= MAX_COOKIE_BYTES) { "The cookie export is too large." }
                    output.write(buffer, 0, read)
                }
                require(archive.nextEntry == null) { "The export has unexpected extra files." }
                return output.toString(Charsets.UTF_8.name())
            }
        }
    }

    fun deleteImportedFile(resolver: ContentResolver, uri: Uri) {
        check(DocumentsContract.deleteDocument(resolver, uri)) {
            "Could not delete the imported file on this phone. Delete it in Files, then retry import."
        }
    }
}
