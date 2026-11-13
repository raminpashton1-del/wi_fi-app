
package com.capitan.phonesync

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.DataOutputStream

object SyncManager {

    private const val API = "https://api.telegram.org"

    suspend fun sendFile(
        context: Context,
        botToken: String,
        chatId: String,
        uri: Uri
    ): Boolean = withContext(Dispatchers.IO) {

        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext false
        }

        val resolver = context.contentResolver

        val fileName = resolver.query(
            uri, null, null, null, null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )
            if (cursor.moveToFirst() && index >= 0)
                cursor.getString(index)
            else "file"
        } ?: "file"

        val mime = resolver.getType(uri) ?: "application/octet-stream"

        try {
            val connection = URL(
                "$API/bot$botToken/sendDocument"
            ).openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            val boundary = "CapitanSyncBoundary"
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
                    output.writeBytes("$value\r\n")
                }

                field("chat_id", chatId)

                output.writeBytes("--$boundary\r\n")
                output.writeBytes(
                    "Content-Disposition: form-data; name=\"document\"; filename=\"$fileName\"\r\n"
                )
                output.writeBytes(
                    "Content-Type: $mime\r\n\r\n"
                )

                resolver.openInputStream(uri)?.use { input ->
                    input.copyTo(output)
                } ?: throw Exception("Cannot open file")

                output.writeBytes("\r\n--$boundary--\r\n")
                output.flush()
            }

            val success = connection.responseCode == 200
            connection.disconnect()
            success

        } catch (e: Exception) {
            false
        }
    }
}
