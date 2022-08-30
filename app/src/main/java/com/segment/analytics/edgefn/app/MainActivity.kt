package com.segment.analytics.edgefn.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.segment.analytics.edgefn.kotlin.EdgeFunctions
import com.segment.analytics.kotlin.android.Analytics

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