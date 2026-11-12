package com.smartclipboard.pro.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Android 10+ never tells a background app that the clipboard changed.
 * So we watch for the user pressing a "Copy" button (click / toast), then ask OverlayService to read the clipboard.
 * No window content is retrieved; only the short label text of the clicked element is compared with a few keywords.
 */
class ClipAccessibilityService : AccessibilityService() {
    private var lastTrigger = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceState.a11yConnected = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceState.a11yConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceState.a11yConnected = false
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.packageName == packageName) return
        val bag = StringBuilder()
        e.text?.forEach { bag.append(it).append(' ') }
        e.contentDescription?.let { bag.append(it) }
        if (bag.isEmpty() || bag.length > 80) return   // long text = chat content, not a button label
        val s = bag.toString().lowercase()
        if (KEYS.none { s.contains(it) }) return
        val now = System.currentTimeMillis()
        if (now - lastTrigger < 500) return
        lastTrigger = now
        ClipBridge.requestRead?.invoke()
    }

    companion object {
        private val KEYS = listOf("کپی", "رونوشت", "copy", "copied")
    }
}
