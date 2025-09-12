package com.iot.flomo

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var switchTurnOffNotification: SwitchCompat
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        switchTurnOffNotification = findViewById(R.id.switchTurnOffNotification)
        prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)

        val isChecked = prefs.getBoolean("notifications_enabled", true)
        switchTurnOffNotification.isChecked = isChecked

        // Save state when changed
        switchTurnOffNotification.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
        }
    }
}