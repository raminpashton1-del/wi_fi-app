
package com.capitan.phonesync

import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.text.InputType

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(
            "capitan_sync", MODE_PRIVATE
        )

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 40, 30, 30)
        }

        val title = TextView(this).apply {
            text = "CAPITAN PHONE SYNC"
            textSize = 24f
        }

        val token = EditText(this).apply {
            hint = "Telegram Bot Token"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("token", ""))
        }

        val chat = EditText(this).apply {
            hint = "Telegram Chat ID"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getString("chat", ""))
        }

        val save = Button(this).apply {
            text = "ذخیره تنظیمات"
            setOnClickListener {
                val botToken = token.text.toString().trim()
                val chatId = chat.text.toString().trim()

                if (botToken.isEmpty() || chatId.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "اطلاعات را کامل وارد کن",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    prefs.edit()
                        .putString("token", botToken)
                        .putString("chat", chatId)
                        .apply()

                    Toast.makeText(
                        this@MainActivity,
                        "تنظیمات ذخیره شد",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        layout.addView(title)
        layout.addView(token)
        layout.addView(chat)
        layout.addView(save)

        setContentView(layout)
    }
}
