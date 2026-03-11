package com.iot.flomo

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileOutputStream
import com.google.firebase.database.*

class ProfileActivity : BaseActivity() {

    private lateinit var btnProfileImage: ImageButton
    private lateinit var btnEditProfileIcon: ImageView
    private val PICK_IMAGE = 1001
    private val PROFILE_FILE_NAME = "profile_image.png"
    private var selectedBitmap: Bitmap? = null

    private val db = FirebaseDatabase.getInstance(
        dbURL
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBackToHome).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
        }

        findViewById<TextView>(R.id.aboutLabel).setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }

        findViewById<ImageView>(R.id.aboutArrow).setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }

        findViewById<TextView>(R.id.logoutLabel).setOnClickListener {
            showLogoutDialog()
        }

        //sharedPref
        val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)
        val userId = sharedPref.getString("userId", null)

        if (userId != null) {
            val database = db.getReference("useraccount").child(userId)

            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Toast.makeText(this@ProfileActivity, "No user data found for $userId", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val user = snapshot.getValue(UserAccount::class.java)

                    if (user != null) {
                        findViewById<TextView>(R.id.txtName).setText(user.name ?: "")
                        findViewById<TextView>(R.id.txtPhone).setText(user.phone ?: "")
                        findViewById<EditText>(R.id.inputName).setText(user.name ?: "")
                        findViewById<EditText>(R.id.inputEmail).setText(user.email ?: "")
                        findViewById<EditText>(R.id.inputPhone).setText(user.phone ?: "")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ProfileActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        btnProfileImage = findViewById(R.id.btnProfileImage)
        btnEditProfileIcon = findViewById(R.id.btnEditProfileIcon)

        loadProfileImage()

        val pickImageClickListener = {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE)
        }
        btnProfileImage.setOnClickListener { pickImageClickListener() }
        btnEditProfileIcon.setOnClickListener { pickImageClickListener() }

        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnSave).setOnClickListener {
            val name = findViewById<EditText>(R.id.inputName).text.toString().trim()
            val email = findViewById<EditText>(R.id.inputEmail).text.toString().trim()
            val phone = findViewById<EditText>(R.id.inputPhone).text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sharedPref = getSharedPreferences("loginPrefs", MODE_PRIVATE)
            val userId = sharedPref.getString("userId", null)

            if (userId != null) {
                val userRef = db.getReference("useraccount").child(userId)

                val updates = mutableMapOf<String, Any>()
                updates["name"] = name
                updates["email"] = email
                updates["phone"] = phone

                if (selectedBitmap != null) {
                    try {
                        saveProfileImage(selectedBitmap!!)
                        Toast.makeText(this, "Profile image updated!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "Error saving profile image", Toast.LENGTH_SHORT).show()
                    }
                }

                userRef.updateChildren(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                        findViewById<TextView>(R.id.txtName).text = name
                        findViewById<TextView>(R.id.txtPhone).text = phone
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "User not found.", Toast.LENGTH_SHORT).show()
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val bitmap = getBitmapFromUri(uri)
                selectedBitmap = bitmap // store temporarily
                btnProfileImage.setImageBitmap(bitmap) // show preview
            }
        }
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT < 28) {
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        } else {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        }
    }

    private fun saveProfileImage(bitmap: Bitmap) {
        val file = File(filesDir, PROFILE_FILE_NAME)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun loadProfileImage() {
        val file = File(filesDir, PROFILE_FILE_NAME)
        if (file.exists()) {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, Uri.fromFile(file))
            btnProfileImage.setImageBitmap(bitmap)
        }
    }

    private fun showLogoutDialog() {

        val titleView = TextView(this).apply {
            text = "Logout"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.red))
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 20)
        }

        val messageView = TextView(this).apply {
            text = "Are you sure you want to logout?"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.dark))
            gravity = Gravity.CENTER
            setPadding(40, 20, 40, 20)
        }

        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton("Confirm") { _, _ ->
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

        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_logout)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(this, R.color.dark))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(ContextCompat.getColor(this, R.color.blue))
    }
}