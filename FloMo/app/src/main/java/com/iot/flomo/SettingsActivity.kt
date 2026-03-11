package com.iot.flomo

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.*

class SettingsActivity : BaseActivity() {

    private lateinit var switchTurnOffNotification: SwitchCompat
    private lateinit var switchSensorNotification: SwitchCompat
    private lateinit var switchThresholdNotification: SwitchCompat
    private lateinit var prefs: SharedPreferences
    private lateinit var inputDailyThreshold: EditText
    private lateinit var inputMonthlyThreshold: EditText
    private lateinit var inputYearlyThreshold: EditText

    private val db = FirebaseDatabase.getInstance(
        dbURL
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBackToHome).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
        }

        prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)

        switchTurnOffNotification = findViewById(R.id.switchTurnOffNotification)
        switchSensorNotification = findViewById(R.id.switchSensorNotification)
        switchThresholdNotification = findViewById(R.id.switchThresholdNotification)

        val leakEnabled = prefs.getBoolean("notifications_enabled", true)
        val sensorEnabled = prefs.getBoolean("sensor_notifications_enabled", true)
        val thresholdEnabled = prefs.getBoolean("threshold_notifications_enabled", true)

        switchTurnOffNotification.isChecked = leakEnabled
        switchSensorNotification.isChecked = sensorEnabled
        switchThresholdNotification.isChecked = thresholdEnabled

        switchTurnOffNotification.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
        }

        switchSensorNotification.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sensor_notifications_enabled", isChecked).apply()
        }

        switchThresholdNotification.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("threshold_notifications_enabled", isChecked).apply()
        }

        inputDailyThreshold = findViewById(R.id.inputDailyThreshold)
        inputMonthlyThreshold = findViewById(R.id.inputMonthlyThreshold)
        inputYearlyThreshold = findViewById(R.id.inputYearlyThreshold)

        val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)
        val userId = sharedPref.getString("userId", null)

        if (userId != null) {
            val thresholdRef = db.getReference("useraccount").child(userId).child("threshold")
            thresholdRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        val defaultThresholds = mapOf(
                            "daily" to 0,
                            "monthly" to 0,
                            "yearly" to 0
                        )
                        thresholdRef.setValue(defaultThresholds)
                        inputDailyThreshold.setText("0")
                        inputMonthlyThreshold.setText("0")
                        inputYearlyThreshold.setText("0")
                        return
                    }

                    inputDailyThreshold.setText(snapshot.child("daily").getValue(Int::class.java)?.toString() ?: "0")
                    inputMonthlyThreshold.setText(snapshot.child("monthly").getValue(Int::class.java)?.toString() ?: "0")
                    inputYearlyThreshold.setText(snapshot.child("yearly").getValue(Int::class.java)?.toString() ?: "0")
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SettingsActivity, "Failed to load thresholds: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })

            findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnSaveThreshold).setOnClickListener {
                val daily = inputDailyThreshold.text.toString().toIntOrNull() ?: 0
                val monthly = inputMonthlyThreshold.text.toString().toIntOrNull() ?: 0
                val yearly = inputYearlyThreshold.text.toString().toIntOrNull() ?: 0

                val updates = mapOf(
                    "daily" to daily,
                    "monthly" to monthly,
                    "yearly" to yearly
                )

                thresholdRef.updateChildren(updates).addOnSuccessListener {
                    Toast.makeText(this, "Thresholds saved successfully!", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(this, "Failed to save thresholds: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        else {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
        }
    }
}