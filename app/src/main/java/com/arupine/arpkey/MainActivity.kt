package com.arupine.arpkey

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up instructions button
        findViewById<ImageButton>(R.id.instructionsButton).setOnClickListener {
            startActivity(Intent(this, InstructionsActivity::class.java))
        }

        // Set up button click listeners
        findViewById<Button>(R.id.enableKeyboardButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.setDefaultKeyboardButton).setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        // Add overlay permission button handler
        findViewById<Button>(R.id.enableOverlayButton).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // Update overlay button text based on current permission status
        updateOverlayButtonStatus()
    }

    override fun onResume() {
        super.onResume()
        // Update button text when returning from settings
        updateOverlayButtonStatus()
    }

    private fun updateOverlayButtonStatus() {
        val overlayButton = findViewById<Button>(R.id.enableOverlayButton)
        if (Settings.canDrawOverlays(this)) {
            overlayButton.text = "Display Over Apps: Enabled"
            overlayButton.isEnabled = false
        } else {
            overlayButton.text = "Enable Display Over Apps"
            overlayButton.isEnabled = true
        }
    }
}