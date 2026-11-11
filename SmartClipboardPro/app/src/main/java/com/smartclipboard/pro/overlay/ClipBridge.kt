package com.smartclipboard.pro.overlay

/** Connects ClipboardReadActivity -> OverlayService (same process). */
object ClipBridge {
    @Volatile var onText: ((String) -> Unit)? = null
    /** Set before we write to the clipboard ourselves, so we don't react to our own change. */
    @Volatile var skipNext: Boolean = false
}
