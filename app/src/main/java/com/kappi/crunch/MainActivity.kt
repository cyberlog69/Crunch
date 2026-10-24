package com.kappi.crunch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.kappi.crunch.theme.CrunchTheme

class MainActivity : ComponentActivity() {

    private var sharedUrlState by mutableStateOf<String?>(null)
    private val urlRegex = Regex("""https?://[^\s<>"]+""")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Notification permission (Android 13+) and legacy storage permission
        // (API 26-28, where finished files are copied to the public Downloads folder).
        val missingPermissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), 101)
        }

        // Handle shared URL from other apps (e.g. YouTube, Facebook, browsers)
        sharedUrlState = extractSharedUrl(intent)

        setContent {
            CrunchTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation(
                        sharedUrl = sharedUrlState,
                        onSharedUrlConsumed = { sharedUrlState = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = extractSharedUrl(intent)
        if (!url.isNullOrBlank()) {
            sharedUrlState = url
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent == null) return null

        val candidates = mutableListOf<String>()

        // 1. Direct intent data URI (ACTION_VIEW / browser)
        intent.dataString?.let { candidates.add(it) }

        // 2. Extra text (standard ACTION_SEND from YouTube, Twitter, Facebook, etc.)
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { candidates.add(it) }

        // 3. Extra subject
        intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let { candidates.add(it) }

        // 4. ClipData
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                item.text?.let { candidates.add(it.toString()) }
                item.uri?.let { candidates.add(it.toString()) }
            }
        }

        // Search for the first valid HTTP/HTTPS URL
        for (candidate in candidates) {
            val match = urlRegex.find(candidate)
            if (match != null) {
                val url = match.value.trimEnd('.', ',', ')', ']', '}', '>', ';', '!', '?', '"', '\'')
                if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                    return url
                }
            }
        }
        return null
    }
}
