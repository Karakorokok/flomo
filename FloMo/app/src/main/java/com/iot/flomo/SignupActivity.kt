package com.iot.flomo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class SignupActivity : BaseActivity() {

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_signup)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = FirebaseDatabase.getInstance(dbURL)
            .getReference("useraccount")

        val nameInput = findViewById<EditText>(R.id.inputName)
        val emailInput = findViewById<EditText>(R.id.inputEmail)
        val phoneInput = findViewById<EditText>(R.id.inputPhone)
        val passwordInput = findViewById<EditText>(R.id.inputPassword)
        val confirmPasswordInput = findViewById<EditText>(R.id.inputConfirmPassword)
        val signupButton = findViewById<Button>(R.id.btnLogin)

        signupButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                showDialog("Signup Error", "Please fill in all fields.")
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                showDialog("Signup Error", "Passwords do not match.")
                return@setOnClickListener
            }

            val user = UserAccount(name, email, phone, password)

            val userId = database.push().key ?: return@setOnClickListener
            database.child(userId).setValue(user)
                .addOnSuccessListener {
                    showDialog("Signup Success", "Account created successfully!") {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                }
                .addOnFailureListener {
                    showDialog("Signup Error", "Failed to create account. firebase error")
                }
        }

        findViewById<ImageButton>(R.id.btnBackToMain).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        findViewById<TextView>(R.id.textToLogin).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showDialog(title: String, message: String, onPositive: (() -> Unit)? = null) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close") { dialogInterface, _ ->
                dialogInterface.dismiss()
                onPositive?.invoke()
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.sky))
    }
}