package com.bugra44bey.livematchanalyzer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var analysisTicker: Runnable? = null
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
                runOnUiThread { showFixtureList(text, liveList, result, true) }
            }.start()
        }


    private fun showFixtureList(body: String, liveList: LinearLayout, result: TextView, isLive: Boolean) {
        try {
            val root = if (body.isBlank()) JSONObject() else JSONObject(body)
            if (root.has("error")) {
                val details = root.optJSONObject("details")
                val apiErrors = details?.optJSONObject("errors")
                val detailText = if (apiErrors != null && apiErrors.length() > 0) {
                    apiErrors.toString()
                } else {
                    details?.toString() ?: root.optString("error")
                }
                result.text = "HATA: " + root.optString("error") + "\nAPI: " + detailText
                return
            }
            val fixtures = root.optJSONArray("fixtures")
            if (fixtures == null || fixtures.length() == 0) {
                val apiErrors = root.optJSONObject("apiErrors")
                result.text = if (apiErrors != null && apiErrors.length() > 0) {
                    "API-Football cevap verdi ama hata döndürdü:\n" + apiErrors.toString()
                } else {
                    "API-Football cevap verdi ama bu sorguda maç bulunamadı."
                }
                return
            }

            result.text = if (isLive) "Canlı maçlar: ${fixtures.length()}  •  Analiz için maça dokun" else "Bugünün maçları: ${fixtures.length()}  •  Analiz için maça dokun"
            val count = minOf(fixtures.length(), 100)

            for (i in 0 until count) {
                val f = fixtures.optJSONObject(i) ?: continue
                val fixture = f.optJSONObject("fixture")
                val teams = f.optJSONObject("teams")
                val goals = f.optJSONObject("goals")
                val score = f.optJSONObject("score")
                val halftime = score?.optJSONObject("halftime")
                val status = fixture?.optJSONObject("status")
                val id = fixture?.optInt("id", 0) ?: 0
                if (id == 0) continue

                val home = teams?.optJSONObject("home")?.optString("name", "?") ?: "?"
                val away = teams?.optJSONObject("away")?.optString("name", "?") ?: "?"
                val hg = goals?.optInt("home", 0) ?: 0
                val ag = goals?.optInt("away", 0) ?: 0
                val minute = status?.optString("elapsed", "") ?: ""

                val button = Button(this)
                button.isAllCaps = false
                button.textSize = 15f
                button.setPadding(18, 8, 18, 8)
                val bg = GradientDrawable()
                bg.setColor(Color.rgb(245, 247, 250))
                bg.cornerRadius = 24f
                button.background = bg
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, 0, 10)
                button.layoutParams = params
                button.text = home + "  " + hg + " - " + ag + "  " + away + (if (minute.isNotEmpty()) "  (" + minute + "') " else "")
                button.setOnClickListener {
                    result.text = "$home - $away analiz ediliyor..."
                    analysisTicker?.let { handler.removeCallbacks(it) }
                    Thread {
                        val analysis = getJson("$apiBase/api/analyze?fixture=$id")
                        runOnUiThread {
                            var liveMinute = minute.toIntOrNull() ?: 0
                            result.text = formatAnalysis(if (analysis.isBlank()) "{}" else analysis, home, away, hg, ag, liveMinute.toString(), halftime?.optInt("home", -1) ?: -1, halftime?.optInt("away", -1) ?: -1)
                            if (isLive) {
                                val ticker = object : Runnable {
                                    override fun run() {
                                        if (liveMinute < 120) liveMinute += 1
                                        result.text = formatAnalysis(if (analysis.isBlank()) "{}" else analysis, home, away, hg, ag, liveMinute.toString(), halftime?.optInt("home", -1) ?: -1, halftime?.optInt("away", -1) ?: -1)
                                        handler.postDelayed(this, 60000)
                                    }
                                }
                                analysisTicker = ticker
                                handler.postDelayed(ticker, 60000)
                            }
                        }
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

    private fun formatAnalysis(body: String, homeName: String, awayName: String, currentHome: Int, currentAway: Int, minuteText: String, halftimeHome: Int, halftimeAway: Int): String {
        return try {
            val root = if (body.isBlank()) JSONObject() else JSONObject(body)
            val predictions = root.optJSONArray("prediction")
            val out = StringBuilder()
            val elapsed = minuteText.toIntOrNull()?.coerceIn(0, 120) ?: 0
            out.append("⚽ " + homeName + "  " + currentHome + " - " + currentAway + "  " + awayName + "\n")
            out.append("⏱ " + elapsed + "'\n\n")
            val totalGoals = currentHome + currentAway

            var expectedTotal = 2.4
            var expectedHomeFinal = 1.2
            var expectedAwayFinal = 1.2
            if (predictions != null && predictions.length() > 0) {
                val p = predictions.optJSONObject(0)
                val pred = p?.optJSONObject("predictions")
                val pg = pred?.optJSONObject("goals")
                val eh = pg?.optString("home", "")?.toDoubleOrNull()
                val ea = pg?.optString("away", "")?.toDoubleOrNull()
                if (eh != null && ea != null && eh + ea > 0.1) {
                    expectedHomeFinal = eh.coerceIn(0.0, 5.0)
                    expectedAwayFinal = ea.coerceIn(0.0, 5.0)
                    expectedTotal = (eh + ea).coerceIn(0.2, 6.0)
                }
            }

            // Canlı skor tahmin edilen takım gollerini aşmışsa, mevcut skor minimum kabul edilir.
            expectedHomeFinal = maxOf(expectedHomeFinal, currentHome.toDouble())
            expectedAwayFinal = maxOf(expectedAwayFinal, currentAway.toDouble())

            val remainingShare = ((90 - elapsed).coerceIn(0, 90) / 90.0)
            val remainingExpected = (expectedTotal * remainingShare).coerceAtLeast(0.05)
            val over25 = if (totalGoals >= 3) 100.0
            else poissonAtLeast(remainingExpected, 3 - totalGoals) * 100.0
            val over35 = if (totalGoals >= 4) 100.0
            else poissonAtLeast(remainingExpected, 4 - totalGoals) * 100.0
            val over45 = if (totalGoals >= 5) 100.0
            else poissonAtLeast(remainingExpected, 5 - totalGoals) * 100.0
            val over55 = if (totalGoals >= 6) 100.0
            else poissonAtLeast(remainingExpected, 6 - totalGoals) * 100.0
            val expectedFinalGoals = totalGoals + remainingExpected
            val btts = if (currentHome > 0 && currentAway > 0) 100.0
            else (1.0 - Math.exp(-remainingExpected * 0.55)).coerceIn(0.0, 1.0) * 100.0

            val firstHalfExpected = (expectedTotal * 0.50).coerceIn(0.05, 3.5)
            val firstHalfTotal = if (elapsed > 45 && halftimeHome >= 0 && halftimeAway >= 0) halftimeHome + halftimeAway else currentHome + currentAway
            val firstHalfOver05 = if (firstHalfTotal >= 1) 100.0
            else poissonAtLeast(firstHalfExpected, 1) * 100.0
            val firstHalfOver15 = if (firstHalfTotal >= 2) 100.0
            else poissonAtLeast(firstHalfExpected, 2 - firstHalfTotal) * 100.0
            val firstHalfBtts = if (elapsed > 45 && halftimeHome >= 0 && halftimeAway >= 0) {
                if (halftimeHome > 0 && halftimeAway > 0) 100.0 else 0.0
            } else if (currentHome > 0 && currentAway > 0 && elapsed <= 45) 100.0
            else if (elapsed > 45) {
                if (currentHome > 0 && currentAway > 0) 100.0 else 0.0
            } else {
                (1.0 - Math.exp(-firstHalfExpected * 0.55)).coerceIn(0.0, 1.0) * 100.0
            }

            val minExpectedGoals = kotlin.math.floor(expectedFinalGoals).toInt().coerceAtLeast(totalGoals)
            val maxExpectedGoals = kotlin.math.ceil(expectedFinalGoals + 0.6).toInt().coerceAtLeast(minExpectedGoals)
            val homeMinGoals = kotlin.math.floor(expectedHomeFinal).toInt().coerceAtLeast(currentHome)
            val homeMaxGoals = kotlin.math.ceil(expectedHomeFinal + 0.4).toInt().coerceAtLeast(homeMinGoals)
            val awayMinGoals = kotlin.math.floor(expectedAwayFinal).toInt().coerceAtLeast(currentAway)
            val awayMaxGoals = kotlin.math.ceil(expectedAwayFinal + 0.4).toInt().coerceAtLeast(awayMinGoals)

            val homeScore1 = if (currentHome >= 1) 100.0 else poissonAtLeast((expectedHomeFinal - currentHome).coerceAtLeast(0.05), 1) * 100.0
            val homeScore2 = if (currentHome >= 2) 100.0 else poissonAtLeast((expectedHomeFinal - currentHome).coerceAtLeast(0.05), 2 - currentHome) * 100.0
            val homeScore3 = if (currentHome >= 3) 100.0 else poissonAtLeast((expectedHomeFinal - currentHome).coerceAtLeast(0.05), 3 - currentHome) * 100.0
            val awayScore1 = if (currentAway >= 1) 100.0 else poissonAtLeast((expectedAwayFinal - currentAway).coerceAtLeast(0.05), 1) * 100.0
            val awayScore2 = if (currentAway >= 2) 100.0 else poissonAtLeast((expectedAwayFinal - currentAway).coerceAtLeast(0.05), 2 - currentAway) * 100.0
            val awayScore3 = if (currentAway >= 3) 100.0 else poissonAtLeast((expectedAwayFinal - currentAway).coerceAtLeast(0.05), 3 - currentAway) * 100.0

            out.append("┌─ ⚽ GOL ANALİZİ ─────────────┐\n")
            out.append("│ Maç sonu: " + minExpectedGoals + "–" + maxExpectedGoals + " gol\n")
            out.append("│ Ev sahibi: " + homeMinGoals + "–" + homeMaxGoals + " gol\n")
            out.append("│ 1+ " + String.format("%.0f", homeScore1) + "%  2+ " + String.format("%.0f", homeScore2) + "%  3+ " + String.format("%.0f", homeScore3) + "%\n")
            out.append("│ Deplasman: " + awayMinGoals + "–" + awayMaxGoals + " gol\n")
            out.append("│ 1+ " + String.format("%.0f", awayScore1) + "%  2+ " + String.format("%.0f", awayScore2) + "%  3+ " + String.format("%.0f", awayScore3) + "%\n")
            out.append("└──────────────────────────────┘\n")
            out.append("┌─ 📊 MAÇ PİYASALARI ──────────┐\n")
            out.append("│ KG Var    " + String.format("%.0f", btts.coerceIn(0.0, 100.0)) + "%\n")
            out.append("│ 2.5 Üst   " + String.format("%.0f", over25.coerceIn(0.0, 100.0)) + "%\n")
            out.append("│ 3.5 Üst   " + String.format("%.0f", over35.coerceIn(0.0, 100.0)) + "%\n")
            out.append("│ 4.5 Üst   " + String.format("%.0f", over45.coerceIn(0.0, 100.0)) + "%\n")
            out.append("│ 5.5 Üst   " + String.format("%.0f", over55.coerceIn(0.0, 100.0)) + "%\n")
            out.append("└──────────────────────────────┘\n")
            if (elapsed <= 45) {
                out.append("İY 0.5 Üst: " + String.format("%.0f", firstHalfOver05.coerceIn(0.0, 100.0)) + "%\n")
                out.append("İY 1.5 Üst: " + String.format("%.0f", firstHalfOver15.coerceIn(0.0, 100.0)) + "%\n")
                out.append("İY KG Var: " + String.format("%.0f", firstHalfBtts.coerceIn(0.0, 100.0)) + "%\n")
            } else {
                out.append("İY 1.5 Üst: İlk yarı tamamlandı\n")
                out.append("İY KG Var: İlk yarı tamamlandı\n")
            }
            out.append("\nBu yüzdeler istatistiksel tahminlerdir; garanti değildir.")
            out.toString()
        } catch (e: Exception) {
            "Analiz verisi okunamadı: ${e.message}"
        }
    }
}