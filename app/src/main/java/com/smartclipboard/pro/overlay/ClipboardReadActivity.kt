package com.smartclipboard.pro.overlay

import android.app.Activity
import android.content.ClipboardManager
import android.os.Bundle

class ClipboardReadActivity : Activity() {
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.postDelayed({ if (!isFinishing) closeNow() }, 1500)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !done) {
            done = true
            window.decorView.postDelayed({
                try {
                    val cm = getSystemService(ClipboardManager::class.java)
                    val clip = cm.primaryClip
                    if (clip != null && clip.itemCount > 0 && clip.description.hasMimeType("text/*")) {
                        val t = clip.getItemAt(0).coerceToText(this)?.toString()
                        if (!t.isNullOrBlank()) ClipBridge.onText?.invoke(t)
                    }
                } catch (_: Exception) { }
                closeNow()
            }, 60)
        }
    }

    private fun closeNow() {
        finish()
        overridePendingTransition(0, 0)
    }
}
