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
                        runOnUiThread { result.text = formatAnalysis(analysis, hg, ag, minute, status?.optString("short", "") ?: "") }
                    }.start()
                }
                liveList.addView(button)
            }
        } catch (e: Exception) {
            result.text = "Canlı maç verisi okunamadı: ${e.message}"
        }
    }

    private fun poissonAtLeast(lambda: Double, k: Int): Double {
        if (k <= 0) return 1.0
        var cdf = 0.0
        var term = Math.exp(-lambda)
        for (i in 0 until k) {
            if (i > 0) term *= lambda / i
            cdf += term
        }
        return (1.0 - cdf).coerceIn(0.0, 1.0)
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

    private fun formatAnalysis(body: String, currentHome: Int, currentAway: Int, minuteText: String, statusShort: String): String {
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
            val elapsed = minuteText.toIntOrNull()?.coerceIn(0, 120) ?: 0
            val totalGoals = currentHome + currentAway

            var expectedTotal = 2.4
            if (predictions != null && predictions.length() > 0) {
                val p = predictions.optJSONObject(0)
                val pred = p?.optJSONObject("predictions")
                val pg = pred?.optJSONObject("goals")
                val eh = pg?.optString("home", "")?.toDoubleOrNull()
                val ea = pg?.optString("away", "")?.toDoubleOrNull()
                if (eh != null && ea != null && eh + ea > 0.1) {
                    expectedTotal = (eh + ea).coerceIn(0.2, 6.0)
                }
            }

            val remainingShare = ((90 - elapsed).coerceIn(0, 90) / 90.0)
            val remainingExpected = (expectedTotal * remainingShare).coerceAtLeast(0.05)
            val over25 = if (totalGoals >= 3) 100.0
            else poissonAtLeast(remainingExpected, 3 - totalGoals) * 100.0
            val btts = if (currentHome > 0 && currentAway > 0) 100.0
            else (1.0 - Math.exp(-remainingExpected * 0.55)).coerceIn(0.0, 1.0) * 100.0

            val firstHalfExpected = (expectedTotal * 0.50).coerceIn(0.05, 3.5)
            val firstHalfTotal = currentHome + currentAway
            val firstHalfOver15 = if (firstHalfTotal >= 2) 100.0
            else poissonAtLeast(firstHalfExpected, 2 - firstHalfTotal) * 100.0
            val firstHalfBtts = if (currentHome > 0 && currentAway > 0 && elapsed <= 45) 100.0
            else if (elapsed > 45) {
                if (currentHome > 0 && currentAway > 0) 100.0 else 0.0
            } else {
                (1.0 - Math.exp(-firstHalfExpected * 0.55)).coerceIn(0.0, 1.0) * 100.0
            }

            out.append("\nHESAPLANAN OLASILIKLAR\n")
            out.append("KG Var: " + String.format("%.0f", btts.coerceIn(0.0, 100.0)) + "%\n")
            out.append("2.5 Üst: " + String.format("%.0f", over25.coerceIn(0.0, 100.0)) + "%\n")
            if (elapsed <= 45) {
                out.append("İY 1.5 Üst: " + String.format("%.0f", firstHalfOver15.coerceIn(0.0, 100.0)) + "%\n")
                out.append("İY KG Var: " + String.format("%.0f", firstHalfBtts.coerceIn(0.0, 100.0)) + "%\n")
            } else {
                out.append("İY 1.5 Üst: İlk yarı tamamlandı\n")
                out.append("İY KG Var: İlk yarı tamamlandı\n")
            }
            out.append("\nBu yüzdeler API tahmini + canlı skor/süre üzerinden hesaplanan tahmini değerlerdir; garanti değildir.")
            out.toString()
        } catch (e: Exception) {
            "Analiz verisi okunamadı: ${e.message}"
        }
    }
}