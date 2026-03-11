package com.iot.flomo

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

open class BaseActivity : AppCompatActivity() {

    //var dbURL = "https://flomo-c5e02-default-rtdb.asia-southeast1.firebasedatabase.app"
    var dbURL = "https://flomo-8203c-default-rtdb.asia-southeast1.firebasedatabase.app"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}