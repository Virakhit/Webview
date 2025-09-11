package com.example.webviewapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val etCompany = findViewById<EditText>(R.id.etCompany)
        val etBrand = findViewById<EditText>(R.id.etBrand)
        val etOutlet = findViewById<EditText>(R.id.etOutlet)
        val btnContinue = findViewById<Button>(R.id.btnContinue)

        btnContinue.setOnClickListener {
            val company = etCompany.text.toString().trim()
            val brand = etBrand.text.toString().trim()
            val outlet = etOutlet.text.toString().trim()

            if (company.isNotEmpty() && brand.isNotEmpty() && outlet.isNotEmpty()) {
                Prefs.save(this, company, brand, outlet)
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } else {
                etCompany.error = if (company.isEmpty()) "Required" else null
                etBrand.error = if (brand.isEmpty()) "Required" else null
                etOutlet.error = if (outlet.isEmpty()) "Required" else null
            }
        }
    }
}
