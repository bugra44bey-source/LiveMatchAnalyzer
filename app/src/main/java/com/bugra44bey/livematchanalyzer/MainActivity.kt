package com.bugra44bey.livematchanalyzer

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private val apiBase = "https://livematchanalyzer.vercel.app"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val liveMatches = findViewById<Button>(R.id.liveMatches)
        val liveList = findViewById<LinearLayout>(R.id.liveList)
        val result = findViewById<TextView>(R.id.result)

        liveMatches.setOnClickListener {
            result.text = "Canlı maçlar yükleniyor..."
            liveList.removeAllViews()
            Thread {
                val text = getJson("$apiBase/api/live")
                runOnUiThread { showLiveMatches(text, liveList, result) }
            }.start()
        }
    }

    private fun showLiveMatches(body: String, liveList: LinearLayout, result: TextView) {
        try {
            val root = JSONObject(body)
            if (root.has("error")) {
                result.text = "Hata: ${root.optString("error")}"
                return
            }
            val fixtures = root.optJSONArray("fixtures")
            if (fixtures == null || fixtures.length() == 0) {
                result.text = "Şu anda canlı maç bulunamadı."
                return
            }

            result.text = "Canlı maçlar: ${fixtures.length()}  •  Analiz için maça dokun"
            val count = minOf(fixtures.length(), 30)

            for (i in 0 until count) {
                val f = fixtures.optJSONObject(i) ?: continue
                val fixture = f.optJSONObject("fixture")
                val teams = f.optJSONObject("teams")
                val goals = f.optJSONObject("goals")
                val status = fixture?.optJSONObject("status")
                val id = fixture?.optInt("id", 0) ?: 0
                if (id == 0) continue

                val home = teams?.optJSONObject("home")?.optString("name", "?") ?: "?"
                val away = teams?.optJSONObject("away")?.optString("name", "?") ?: "?"
                val hg = goals?.optInt("home", 0) ?: 0
                val ag = goals?.optInt("away", 0) ?: 0
                val minute = status?.optString("elapsed", "") ?: ""

                val button = Button(this)
                button.text = "$home $hg - $ag $away${if (minute.isNotEmpty()) "  (${minute}') " else ""}"
                button.setOnClickListener {
                    result.text = "$home - $away analiz ediliyor..."
                    Thread {
                        val analysis = getJson("$apiBase/api/analyze?fixture=$id")
                        runOnUiThread { result.text = formatAnalysis(analysis) }
                    }.start()
                }
                liveList.addView(button)
            }
        } catch (e: Exception) {
            result.text = "Canlı maç verisi okunamadı: ${e.message}"
        }
    }

    private fun getJson(urlString: String): String {
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: """{"error":"Boş cevap"}"""
            connection.disconnect()
            body
        } catch (e: Exception) {
            """{"error":${JSONObject.quote(e.message ?: "Bağlantı hatası")}}"""
        }
    }

    private fun formatAnalysis(body: String): String {
        return try {
            val root = JSONObject(body)
            if (root.has("error")) return "Hata: ${root.optString("error")}"
            val predictions = root.optJSONArray("prediction")
            val out = StringBuilder("MAÇ ANALİZİ\n\n")
            if (predictions != null && predictions.length() > 0) {
                val p = predictions.optJSONObject(0)
                val pred = p?.optJSONObject("predictions")
                val goals = pred?.optJSONObject("goals")
                val advice = pred?.optString("advice", "") ?: ""
                val underOver = pred?.optString("under_over", "") ?: ""
                out.append("Tahmin: ${pred?.optString("winner", "—")}\n")
                out.append("Gol tahmini: ${goals?.optString("home", "—")} - ${goals?.optString("away", "—")}\n")
                out.append("Alt/Üst: $underOver\n")
                if (advice.isNotEmpty()) out.append("Öneri: $advice\n")
            } else {
                out.append("Bu maç için API-Football tahmini bulunamadı.\n")
            }
            out.append("\nNot: KG Var ve 2.5 Üst değerlendirmesi canlı istatistiklerle ayrıca geliştirilecek.")
            out.toString()
        } catch (e: Exception) {
            "Analiz verisi okunamadı: ${e.message}"
        }
    }
}