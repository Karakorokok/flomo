package com.iot.flomo

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.*

class LoginActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = FirebaseDatabase.getInstance("https://flomo-c5e02-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("useraccount")

        findViewById<ImageButton>(R.id.btnBackToMain).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<TextView>(R.id.textToSignup).setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        val emailField = findViewById<EditText>(R.id.inputEmail)
        val passwordField = findViewById<EditText>(R.id.inputPassword)
        val rememberMeCheckBox = findViewById<CheckBox>(R.id.checkboxRememberMe)
        rememberMeCheckBox.buttonTintList = ContextCompat.getColorStateList(this, R.color.blue)

        findViewById<TextView>(R.id.btnLogin).setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showDialog("Login Error", "Please enter both email and password.")
                return@setOnClickListener
            }

            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var isMatch = false
                    for (userSnapshot in snapshot.children) {
                        val user = userSnapshot.getValue(UserAccount::class.java)
                        if (user?.email == email && user.password == password) {
                            isMatch = true
                            break
                        }
                    }

                    if (isMatch) {
                        if (rememberMeCheckBox.isChecked) {
                            val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)
                            sharedPref.edit().apply {
                                putBoolean("isLoggedIn", true)
                                putString("userEmail", email)
                                apply()
                            }
                        }
                        val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    else {
                        showDialog("Login Error", "Invalid email or password.")
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    showDialog("Login Error", "Something went wrong: ${error.message}")
                }
            })
        }
    }

    private fun showDialog(title: String, message: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.sky))
    }

    override fun onStart() {
        super.onStart()
        val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)
        if (isLoggedIn) {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

}
