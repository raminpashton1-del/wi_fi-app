
package com.capitan.phonesync

import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.text.InputType
import android.view.View
import android.graphics.Color
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class MainActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var token: EditText
    private lateinit var chat: EditText
    private lateinit var status: TextView

    private val prefs by lazy {
        getSharedPreferences("capitan_sync", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 40, 30, 30)
        }

        val title = TextView(this).apply {
            text = "CAPITAN PHONE SYNC"
            textSize = 24f
            setTextColor(Color.BLACK)
        }

        token = EditText(this).apply {
            hint = "Telegram Bot Token"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("token", ""))
        }

        chat = EditText(this).apply {
            hint = "Telegram Chat ID"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getString("chat", "7177758786"))
        }

        status = TextView(this).apply {
            text = "وضعیت: آماده"
            textSize = 16f
        }

        val save = Button(this).apply {
            text = "ذخیره تنظیمات"
            setOnClickListener {
                saveSettings()
            }
        }

        val test = Button(this).apply {
            text = "آزمایش اتصال تلگرام"
            setOnClickListener {
                testTelegram()
            }
        }

        layout.addView(title)
        layout.addView(token)
        layout.addView(chat)
        layout.addView(save)
        layout.addView(test)
        layout.addView(status)

        setContentView(layout)
    }

    private fun saveSettings() {
        val t = token.text.toString().trim()
        val c = chat.text.toString().trim()

        if (t.isEmpty() || c.isEmpty()) {
            status.text = "اطلاعات را کامل وارد کن"
            return
        }

        prefs.edit()
            .putString("token", t)
            .putString("chat", c)
            .apply()

        status.text = "تنظیمات ذخیره شد"
    }

    private fun testTelegram() {
        val t = token.text.toString().trim()
        val c = chat.text.toString().trim()

        if (t.isEmpty() || c.isEmpty()) {
            status.text = "توکن و Chat ID را وارد کن"
            return
        }

        status.text = "در حال آزمایش اتصال..."

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = URL(
                        "https://api.telegram.org/bot$t/sendMessage"
                    )

                    val connection =
                        url.openConnection() as HttpURLConnection

                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                    )

                    val body = JSONObject().apply {
                        put("chat_id", c)
                        put(
                            "text",
                            "CAPITAN PHONE SYNC\nConnection successful!"
                        )
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
                        } ?: "HTTP ${connection.responseCode}"
                    }

                    connection.disconnect()

                    val json = JSONObject(response)
                    json.optBoolean("ok", false)

                } catch (e: Exception) {
                    false
                }
            }

            status.text = if (result) {
                "اتصال موفق! پیام به تلگرام ارسال شد."
            } else {
                "اتصال ناموفق! توکن، شناسه و اینترنت را بررسی کن."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
