package com.example.screentranslator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val requestCode = 1001
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val apiEdit = findViewById<EditText>(R.id.apiEdit)
        apiEdit.setText(prefs.getString("api", "https://libretranslate.com"))

        findViewById<Button>(R.id.overlayButton).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }

        findViewById<Button>(R.id.saveApiButton).setOnClickListener {
            val value = apiEdit.text.toString().trim().trimEnd('/')
            if (value.isNotEmpty()) prefs.edit().putString("api", value).apply()
        }

        findViewById<Button>(R.id.captureButton).setOnClickListener {
            val value = apiEdit.text.toString().trim().trimEnd('/')
            if (value.isNotEmpty()) prefs.edit().putString("api", value).apply()
            val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(pm.createScreenCaptureIntent(), requestCode)
        }
    }

    @Deprecated("Uses the simple activity-result callback for compatibility")
    override fun onActivityResult(req: Int, result: Int, data: Intent?) {
        super.onActivityResult(req, result, data)
        if (req == requestCode && result == Activity.RESULT_OK && data != null) {
            startForegroundService(Intent(this, TranslatorService::class.java).apply {
                action = TranslatorService.START
                putExtra(TranslatorService.CODE, result)
                putExtra(TranslatorService.DATA, data)
            })
        }
    }
}
