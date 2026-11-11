package com.smartclipboard.pro.overlay

import android.app.Activity
import android.content.ClipboardManager

/**
 * Android 10+ only lets the focused app read the clipboard. This transparent activity
 * gains focus for a split second, reads the clip, and closes.
 * (Apps holding the overlay permission may start activities from the background.)
 */
class ClipboardReadActivity : Activity() {
    private var done = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !done) {
            done = true
            try {
                val cm = getSystemService(ClipboardManager::class.java)
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0 && clip.description.hasMimeType("text/*")) {
                    val t = clip.getItemAt(0).coerceToText(this)?.toString()
                    if (!t.isNullOrBlank()) ClipBridge.onText?.invoke(t)
                }
            } catch (_: Exception) { }
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
