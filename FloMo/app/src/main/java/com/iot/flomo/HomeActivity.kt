package com.iot.flomo

import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
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
import androidx.constraintlayout.widget.ConstraintSet
import androidx.transition.TransitionManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.transition.AutoTransition
import java.util.*
import com.github.chrisbanes.photoview.PhotoView

class HomeActivity : BaseActivity() {
    private var isPhExpanded = false
    private var isTDSExpanded = false
    private var isTurbidityExpanded = false
    private var dailyNotified = false
    private var monthlyNotified = false
    private var yearlyNotified = false
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private var currentPh: Float? = null
    private var currentTds: Int? = null
    private var currentTurbidity: Int? = null
    private val db = FirebaseDatabase.getInstance(dbURL)

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
        phCardExpansion()
        tdsCardExpansion()
        turbidityCardExpansion()

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

        val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)
        val userId = sharedPref.getString("userId", null)

        // menu
        val menuButton = findViewById<ImageButton>(R.id.menuButton)
        menuButton.setOnClickListener { view ->
            val popup = androidx.appcompat.widget.PopupMenu(this, view, Gravity.END)
            popup.menuInflater.inflate(R.menu.menu_main, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_profile -> {
                        val intent = Intent(this, ProfileActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.action_settings -> {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        val txtLogs = findViewById<TextView>(R.id.txtLogs)
            txtLogs.setOnClickListener { view ->
                showLogs()
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
                currentPh = phValue
                updateOverallStatus()
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
                showPhNotification(phValue)
                saveHistory("ph", phValue.toString())
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
                currentTds = tds
                updateOverallStatus()
                tdsValueText.text = "$tds ppm"
                tdsStatusText.text = when {
                    tds < 500 -> "Safe to drink"
                    tds in 500..900 -> "Warning"
                    else -> "Unsafe to drink"
                }
                showTDSNotification(tds)
                saveHistory("tds", tds.toString())
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "Error loading TDS data", Toast.LENGTH_SHORT).show()
            }
        })
        tdsStatusText.visibility = View.GONE

        // Turbidity
        val turbidityValueText = findViewById<TextView>(R.id.turbidityValue)
        db.getReference("data/turbidity").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val turbidity = snapshot.getValue()?.toString()?.toIntOrNull() ?: return
                currentTurbidity = turbidity
                updateOverallStatus()
                val status = when (snapshot.getValue()?.toString()?.toIntOrNull()) {
                    1 -> "Clear"
                    2 -> "Cloudy"
                    3 -> "Murky"
                    else -> "---"
                }
                turbidityValueText.text = status
                showTurbidityNotification(turbidity)
                saveHistory("turbidity", turbidity.toString())
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

                val dailyLiters = (dailyTotal / 100f) * 26f
                val monthlyLiters = (monthlyTotal / 100f) * 26f
                val yearlyLiters = (yearlyTotal / 100f) * 26f

                dailyText.text = "${String.format("%.1f", dailyLiters)} L"
                monthlyText.text = "${String.format("%.1f", monthlyLiters)} L"
                yearlyText.text = "${String.format("%.1f", yearlyLiters)} L"

                checkConsumptionThresholds(dailyLiters, monthlyLiters, yearlyLiters)

            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val leakChannel = NotificationChannel(
                "leakage_channel",
                "Leakage Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for possible water leakage"
            }
            manager.createNotificationChannel(leakChannel)

            val defaultChannel = NotificationChannel(
                "default_channel",
                "Sensor Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for pH, TDS, and Turbidity alerts"
            }
            manager.createNotificationChannel(defaultChannel)
        }
    }

    private fun showPhNotification(ph: Float) {
        when {
            ph < 6 -> {
                showSensorNotification("pH Alert", "Water is acidic (pH $ph)")
            }
            ph > 8 -> {
                showSensorNotification("pH Alert", "Water is alkaline (pH $ph)")
            }
        }
    }

    private fun showTDSNotification(tds: Int) {
        if (tds > 500) {
            showSensorNotification("TDS Alert", "TDS $tds ppm – Not suitable for drinking")
        }
    }

    private fun showTurbidityNotification(turbidity: Int) {
        when (turbidity) {
            2 -> showSensorNotification("Turbidity Alert", "Water is cloudy, Not suitable for drinking")
            3 -> showSensorNotification("Turbidity Alert", "Water is murky, Not suitable for drinking")
        }
    }

    private fun showLeakageNotification() {
        showLeakNotification("Leakage Detected!", "Possible water leakage has been detected.")
    }
    private fun showLeakNotification(title: String, message: String) {

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)

        if (!notificationsEnabled) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // No permission, don't crash
        }

        val builder = NotificationCompat.Builder(this, "leakage_channel")
            .setSmallIcon(R.drawable.leak)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(Random().nextInt(), builder.build())
        }
    }

    private fun showSensorNotification(title: String, message: String) {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val sensorsEnabled = prefs.getBoolean("sensor_notifications_enabled", true)

        if (!sensorsEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(this, "default_channel")
            .setSmallIcon(R.drawable.leak)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(Random().nextInt(), builder.build())
        }
    }

    private fun checkConsumptionThresholds(dailyLiters: Float, monthlyLiters: Float, yearlyLiters: Float) {
        val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)
        val userId = sharedPref.getString("userId", null) ?: return

        val thresholdRef = db.getReference("useraccount").child(userId).child("threshold")

        val philippineTimeZone = TimeZone.getTimeZone("Asia/Manila")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault())
        dateFormat.timeZone = philippineTimeZone
        val currentTime = dateFormat.format(Date())

        thresholdRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dailyThreshold = snapshot.child("daily").getValue(Float::class.java) ?: Float.MAX_VALUE
                val monthlyThreshold = snapshot.child("monthly").getValue(Float::class.java) ?: Float.MAX_VALUE
                val yearlyThreshold = snapshot.child("yearly").getValue(Float::class.java) ?: Float.MAX_VALUE

                // DAILY
                if (dailyLiters > dailyThreshold && !dailyNotified) {
                    showConsumptionNotification(
                        "Daily Consumption Alert",
                        "Daily consumption $dailyLiters L exceeded threshold $dailyThreshold L at $currentTime"
                    )
                    dailyNotified = true
                }
                else if (dailyLiters <= dailyThreshold) {
                    dailyNotified = false
                }

                // MONTHLY
                if (monthlyLiters > monthlyThreshold && !monthlyNotified) {
                    showConsumptionNotification(
                        "Monthly Consumption Alert",
                        "Monthly consumption $monthlyLiters L exceeded threshold $monthlyThreshold L at $currentTime"
                    )
                    monthlyNotified = true
                }
                else if (monthlyLiters <= monthlyThreshold) {
                    monthlyNotified = false
                }

                // YEARLY
                if (yearlyLiters > yearlyThreshold && !yearlyNotified) {
                    showConsumptionNotification(
                        "Yearly Consumption Alert",
                        "Yearly consumption $yearlyLiters L exceeded threshold $yearlyThreshold L at $currentTime"
                    )
                    yearlyNotified = true
                }
                else if (yearlyLiters <= yearlyThreshold) {
                    yearlyNotified = false
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showConsumptionNotification(title: String, message: String) {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("threshold_notifications_enabled", true)
        if (!notificationsEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val builder = NotificationCompat.Builder(this, "default_channel")
            .setSmallIcon(R.drawable.leak)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(this).notify(Random().nextInt(), builder.build())
    }

    private fun phCardExpansion() {
        val phCard = findViewById<FrameLayout>(R.id.phCard)
        val phScale = findViewById<PhotoView>(R.id.phScale)
        val phProgress = findViewById<ProgressBar>(R.id.circularProgress)
        val perText = findViewById<TextView>(R.id.percentageText)

        phCard.setOnClickListener {
            if (!isPhExpanded) {
                phCard.pivotX = 0f   // left edge stays fixed
                phCard.pivotY = 0f   // top edge stays fixed

                phCard.animate()
                    .setDuration(300)
                    .scaleX(2.06f)   // wider to the right
                    .scaleY(2.06f)   // taller downward
                    .start()

                phCard.elevation = 20f
                phScale.visibility = View.VISIBLE
                phProgress.visibility = View.GONE
                perText.visibility = View.GONE
                isPhExpanded = true
            }
            else {
                phCard.animate()
                    .setDuration(300)
                    .scaleX(1f)
                    .scaleY(1f)
                    .start()

                phCard.elevation = 0f
                phScale.visibility = View.GONE
                phProgress.visibility = View.VISIBLE
                perText.visibility = View.VISIBLE
                isPhExpanded = false
            }
        }
    }

    private fun tdsCardExpansion() {
        val tdsCard = findViewById<FrameLayout>(R.id.tdsCard)
        val tdsScale = findViewById<PhotoView>(R.id.tdsScale)
        val tdsValue = findViewById<TextView>(R.id.tdsValue)
        val tdsStatus = findViewById<TextView>(R.id.tdsStatus)

        tdsCard.setOnClickListener {
            if (!isTDSExpanded) {
                tdsCard.pivotX = tdsCard.width.toFloat()   // right edge stays fixed
                tdsCard.pivotY = 0f   // top edge stays fixed

                tdsCard.animate()
                    .setDuration(300)
                    .scaleX(2.06f)   // wider to the left
                    .scaleY(2.06f)   // taller downward
                    .start()

                tdsCard.elevation = 20f
                tdsScale.visibility = View.VISIBLE
                tdsValue.visibility = View.GONE
                tdsStatus.visibility = View.GONE
                isTDSExpanded = true
            }
            else {
                tdsCard.animate()
                    .setDuration(300)
                    .scaleX(1f)
                    .scaleY(1f)
                    .start()

                tdsCard.elevation = 0f
                tdsScale.visibility = View.GONE
                tdsValue.visibility = View.VISIBLE
                tdsStatus.visibility = View.GONE
                isTDSExpanded = false
            }
        }
    }

    private fun turbidityCardExpansion() {
        val turbidityCard = findViewById<FrameLayout>(R.id.turbidityCard)
        val turbidityScale = findViewById<PhotoView>(R.id.turbidityScale)
        val turbidityValue = findViewById<TextView>(R.id.turbidityValue)

        turbidityCard.setOnClickListener {
            if (!isTurbidityExpanded) {
                turbidityCard.pivotX = turbidityCard.width.toFloat()   // right edge stays fixed
                turbidityCard.pivotY = 0f   // top edge stays fixed

                turbidityCard.animate()
                    .setDuration(300)
                    .scaleX(2.06f)   // wider to the left
                    .scaleY(2.06f)   // taller downward
                    .start()

                turbidityCard.elevation = 20f
                turbidityScale.visibility = View.VISIBLE
                turbidityValue.visibility = View.GONE
                isTurbidityExpanded = true
            }
            else {
                turbidityCard.animate()
                    .setDuration(300)
                    .scaleX(1f)
                    .scaleY(1f)
                    .start()

                turbidityCard.elevation = 0f
                turbidityScale.visibility = View.GONE
                turbidityValue.visibility = View.VISIBLE
                isTurbidityExpanded = false
            }
        }
    }

    data class SensorLog(
        val timestamp: String,
        val ph: String?,
        val tds: String?,
        val turbidity: String?
    )

    private fun showLogs() {

        val dialogView = layoutInflater.inflate(R.layout.logs_popup, null)
        val tableLayout = dialogView.findViewById<TableLayout>(R.id.tableLogs)
        val typeface = ResourcesCompat.getFont(this, R.font.goldman_regular)

        val header = TableRow(this)
        listOf("Timestamp", "pH", "TDS", "Turbidity").forEach { text ->
            val tv = TextView(this)
            tv.text = text
            tv.typeface = typeface
            tv.setPadding(16, 16, 16, 16)
            header.addView(tv)
        }
        tableLayout.addView(header)

        val historyRef = db.getReference("history")
        val logs = mutableMapOf<String, SensorLog>()
        fun fetchLogs(sensor: String, onComplete: () -> Unit) {
            historyRef.child(sensor).limitToLast(100)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        snapshot.children.forEach { child ->
                            val timestamp = child.child("timestamp").getValue(String::class.java) ?: return@forEach
                            val value = child.child("value").getValue(String::class.java) ?: "-"
                            val existing = logs[timestamp]
                            val updated = when (sensor) {
                                "ph" -> existing?.copy(ph = value) ?: SensorLog(timestamp, value, null, null)
                                "tds" -> existing?.copy(tds = value) ?: SensorLog(timestamp, null, value, null)
                                "turbidity" -> existing?.copy(turbidity = value) ?: SensorLog(timestamp, null, null, value)
                                else -> existing
                            }
                            if (updated != null) logs[timestamp] = updated
                        }
                        onComplete()
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        fetchLogs("ph") {
            fetchLogs("tds") {
                fetchLogs("turbidity") {
                    val sortedLogs = logs.values.sortedByDescending {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(it.timestamp)
                    }.take(100)

                    sortedLogs.forEach { log ->
                        val row = TableRow(this)
                        listOf(log.timestamp, log.ph ?: "-", log.tds ?: "-", log.turbidity ?: "-").forEach { value ->
                            val tv = TextView(this)
                            tv.text = value
                            tv.setPadding(16, 16, 16, 16)
                            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            row.addView(tv)
                        }
                        tableLayout.addView(row)
                    }

                    AlertDialog.Builder(this)
//                        .setTitle("Sensor Logs (Latest ${sortedLogs.size} Logs)")
                        .setTitle("Sensor Logs")
                        .setView(dialogView)
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
        }
    }

    private fun updateOverallStatus() {

        val overallStatus = findViewById<TextView>(R.id.txtOverallStatus)
        val ph = currentPh
        val tds = currentTds
        val turbidity = currentTurbidity

        if (ph != null && tds != null && turbidity != null) {

            val safe = (ph > 6.5f && ph < 8.5f) && (tds < 500) && (turbidity == 1)

            val statusText = if (safe) "Safe to drink" else "Not safe to drink"
            val color = if (safe) Color.GREEN else Color.RED

            val fullText = "Status: $statusText"
            val spannable = android.text.SpannableString(fullText)
            
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(Color.WHITE),
                0,
                7,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannable.setSpan(
                android.text.style.ForegroundColorSpan(color),
                8,
                fullText.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            overallStatus.text = spannable
        }
    }

}
