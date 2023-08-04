package com.segment.analytics.liveplugins.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    val analytics = MainApplication.analytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.btn_checkout).setOnClickListener {
            analytics.track("User Checkout")
        }

        findViewById<TextView>(R.id.btn_exit).setOnClickListener {
            analytics.track("Exit Clicked")
        }

        findViewById<TextView>(R.id.btn_purchase).setOnClickListener {
            analytics.track("Item Purchased")
        }

        findViewById<TextView>(R.id.btn_register).setOnClickListener {
            analytics.track("User Registered")
        }
    }
}