package com.bugra44bey.livematchanalyzer
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
class MainActivity: AppCompatActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContentView(R.layout.activity_main)
  val home=findViewById<EditText>(R.id.home)
  val away=findViewById<EditText>(R.id.away)
  val result=findViewById<TextView>(R.id.result)
  findViewById<Button>(R.id.analyze).setOnClickListener {
   result.text="${home.text} - ${away.text}

KG Var: analiz bekleniyor
2.5 Üst: analiz bekleniyor
İY 1.5 Üst: analiz bekleniyor"
  }
 }
}