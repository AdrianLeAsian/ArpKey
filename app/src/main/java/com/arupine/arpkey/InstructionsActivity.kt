package com.arupine.arpkey

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class InstructionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instructions)

        // Set up back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }
    }
} 