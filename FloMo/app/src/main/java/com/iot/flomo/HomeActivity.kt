package com.iot.flomo

import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val db = FirebaseDatabase.getInstance(
        "https://flomo-c5e02-default-rtdb.asia-southeast1.firebasedatabase.app"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
        }

        // menu
        val menuButton = findViewById<ImageButton>(R.id.menuButton)
        menuButton.setOnClickListener { view ->
            val popup = androidx.appcompat.widget.PopupMenu(this, view, Gravity.END)
            popup.menuInflater.inflate(R.menu.menu_main, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_profile -> {
                        // TODO: Launch profile activity
                        true
                    }
                    R.id.action_settings -> {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.action_logout -> {
                        showLogoutDialog()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // switch
        val statusRef = db.getReference("data/status")
        val overrideSwitch = findViewById<SwitchCompat>(R.id.overrideSwitch)

        overrideSwitch.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "on" else "off"
            statusRef.setValue(status)
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
                }
        }

        statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                val shouldBeChecked = status.equals("on", ignoreCase = true)
                if (overrideSwitch.isChecked != shouldBeChecked) {
                    overrideSwitch.isChecked = shouldBeChecked
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "Failed to read status", Toast.LENGTH_SHORT).show()
            }
        })

        // pH
        val phRef = db.getReference("data/ph")
        val circularProgress = findViewById<ProgressBar>(R.id.circularProgress)
        val percentageText = findViewById<TextView>(R.id.percentageText)

        phRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val phValue = snapshot.getValue()?.toString()?.toFloatOrNull()?.coerceIn(0f, 14f) ?: return
                val targetProgress = ((phValue / 14f) * 100).toInt()
                val currentProgress = circularProgress.progress
                val progressDelta = kotlin.math.abs(targetProgress - currentProgress)
                val dynamicDuration = (progressDelta * 20).coerceIn(200, 1000)

                ObjectAnimator.ofInt(circularProgress, "progress", currentProgress, targetProgress).apply {
                    duration = dynamicDuration.toLong()
                    interpolator = DecelerateInterpolator()
                    start()
                }
                percentageText.text = String.format("%.2f", phValue)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "Error loading pH data", Toast.LENGTH_SHORT).show()
            }
        })

        // waterLevel + consumption
        val waterProgress = findViewById<ProgressBar>(R.id.waterProgress)
        val waterText = findViewById<TextView>(R.id.waterPercentText)
        val waterLevelRef = db.getReference("data/waterlevel")
        val consumptionRef = db.getReference("data/waterConsumption")

        var previousLevel: Float? = null
        var totalConsumption = 0f

        consumptionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                totalConsumption = snapshot.getValue()?.toString()?.toFloatOrNull() ?: 0f
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        waterLevelRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val level = snapshot.getValue()?.toString()?.toFloatOrNull()?.coerceIn(0f, 100f) ?: return
                val levelInt = level.toInt()

                ObjectAnimator.ofInt(waterProgress, "progress", waterProgress.progress, levelInt).apply {
                    duration = 500
                    interpolator = DecelerateInterpolator()
                    start()
                }
                waterText.text = "$levelInt%"

                if (levelInt >= 100) {
                    statusRef.setValue("off")
                }
                else {
                    statusRef.setValue("on")
                }

                previousLevel?.let { prev ->
                    val delta = prev - level
                    if (delta > 0f && delta <= 100f) {
                        totalConsumption += delta
                        consumptionRef.setValue(String.format("%.2f", totalConsumption))
                        saveHistory("consumption", totalConsumption.toString())
                    }
                }

                previousLevel = level
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "Error loading water level", Toast.LENGTH_SHORT).show()
            }
        })

        // TDS
        val tdsValueText = findViewById<TextView>(R.id.tdsValue)
        val tdsStatusText = findViewById<TextView>(R.id.tdsStatus)
        db.getReference("data/tds").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tds = snapshot.getValue()?.toString()?.toIntOrNull() ?: return
                tdsValueText.text = "$tds ppm"
                tdsStatusText.text = when {
                    tds < 500 -> "Safe to drink"
                    tds in 500..900 -> "Warning"
                    else -> "Unsafe to drink"
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "Error loading TDS data", Toast.LENGTH_SHORT).show()
            }
        })

        // Turbidity
        val turbidityValueText = findViewById<TextView>(R.id.turbidityValue)
        db.getReference("data/turbidity").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = when (snapshot.getValue()?.toString()?.toIntOrNull()) {
                    1 -> "Clear"
                    2 -> "Cloudy"
                    3 -> "Murky"
                    else -> "---"
                }
                turbidityValueText.text = status
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "Error loading turbidity data", Toast.LENGTH_SHORT).show()
            }
        })

        // leakage detection
        val infraredRef = db.getReference("data/infrared")
        val waterLevelRefLeak = waterLevelRef
        var lastWaterLevelLeak: Float? = null
        var currentInfraredLeak: Float? = null

        fun checkLeakCondition() {
            val infraredOk = currentInfraredLeak != null && currentInfraredLeak!! < 150
            if (infraredOk && lastWaterLevelLeak != null) {
                showLeakageNotification()
            }
        }

        infraredRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentInfraredLeak = snapshot.getValue()?.toString()?.toFloatOrNull()
                checkLeakCondition()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        waterLevelRefLeak.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentWaterLevel = snapshot.getValue()?.toString()?.toFloatOrNull()
                if (lastWaterLevelLeak != null && currentWaterLevel != null) {
                    if (currentWaterLevel < lastWaterLevelLeak!!) {
                        checkLeakCondition()
                    }
                }
                lastWaterLevelLeak = currentWaterLevel
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        loadAggregates()
    }

    private fun saveHistory(sensor: String, value: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val historyRef = db.getReference("history").child(sensor)
        val record = mapOf(
            "timestamp" to timestamp,
            "value" to value
        )
        historyRef.push().setValue(record)
    }

    private fun showLogoutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Logout Confirmation")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)
                sharedPref.edit().clear().apply()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.blue))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(getColor(R.color.dark))
    }

    private fun loadAggregates() {
        val dailyText = findViewById<TextView>(R.id.dailyValue)
        val monthlyText = findViewById<TextView>(R.id.monthlyValue)
        val yearlyText = findViewById<TextView>(R.id.yearlyValue)

        val historyRef = db.getReference("history/consumption")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        historyRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var dailyTotal = 0f
                var monthlyTotal = 0f
                var yearlyTotal = 0f

                val calendar = Calendar.getInstance()
                val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
                val currentMonth = calendar.get(Calendar.MONTH)
                val currentYear = calendar.get(Calendar.YEAR)

                var lastValue: Float? = null

                snapshot.children.sortedBy { it.child("timestamp").value.toString() }.forEach { child ->
                    val timestampStr = child.child("timestamp").getValue(String::class.java) ?: return@forEach
                    val valueStr = child.child("value").getValue(String::class.java) ?: return@forEach

                    val date = dateFormat.parse(timestampStr) ?: return@forEach
                    val value = valueStr.toFloatOrNull() ?: return@forEach

                    // Calculate delta (difference from last record)
                    if (lastValue != null) {
                        val delta = value - lastValue!!
                        if (delta > 0) {
                            val recordCal = Calendar.getInstance().apply { time = date }

                            if (recordCal.get(Calendar.YEAR) == currentYear) {
                                yearlyTotal += delta
                                if (recordCal.get(Calendar.MONTH) == currentMonth) {
                                    monthlyTotal += delta
                                    if (recordCal.get(Calendar.DAY_OF_MONTH) == currentDay) {
                                        dailyTotal += delta
                                    }
                                }
                            }
                        }
                    }
                    lastValue = value
                }

                dailyText.text = "${String.format("%.1f", dailyTotal)} %"
                monthlyText.text = "${String.format("%.1f", monthlyTotal)} %"
                yearlyText.text = "${String.format("%.1f", yearlyTotal)} %"
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "leakage_channel", // channel ID
                "Leakage Alerts",  // channel name
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for possible water leakage"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showLeakageNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // No permission, don't crash
        }

        val builder = NotificationCompat.Builder(this, "leakage_channel")
            .setSmallIcon(R.drawable.leak)
            .setContentTitle("Leakage Detected!")
            .setContentText("Possible water leakage has been detected.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build())
        }
    }

    private fun showNotification(title: String, message: String) {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)

        if (!notificationsEnabled) {
            return
        }

        val builder = NotificationCompat.Builder(this, "default_channel")
            .setSmallIcon(R.drawable.leak)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build())
        }
    }

}
