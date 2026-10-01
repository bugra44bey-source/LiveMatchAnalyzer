package com.bugra44bey.livematchanalyzer

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val home = findViewById<EditText>(R.id.home)
        val away = findViewById<EditText>(R.id.away)
        val result = findViewById<TextView>(R.id.result)

        findViewById<Button>(R.id.analyze).setOnClickListener {
            result.text = """
                ${home.text} - ${away.text}

                KG Var: canlı veri bağlantısı hazırlanıyor
                2.5 Üst: canlı veri bağlantısı hazırlanıyor
                İY 1.5 Üst: canlı veri bağlantısı hazırlanıyor
            """.trimIndent()
        }
    }
}
