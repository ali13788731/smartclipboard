package com.smartclipboard.pro.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.smartclipboard.pro.MainActivity
import com.smartclipboard.pro.ui.OverlayActions
import com.smartclipboard.pro.ui.OverlayRoot
import kotlin.math.roundToInt

enum class OverlayMode { BUBBLE, SMART, EDITOR }

class OverlayService : Service(), OverlayActions {

    private lateinit var wm: WindowManager
    private lateinit var cm: ClipboardManager
    private lateinit var lp: WindowManager.LayoutParams
    private val owner = ServiceOwner()
    private val handler = Handler(Looper.getMainLooper())
    private var composeView: ComposeView? = null

    private var mode by mutableStateOf(OverlayMode.BUBBLE)
    private var text by mutableStateOf("")
    private var lastText: String? = null

    private var bubbleX = 0
    private var bubbleY = 300
    private var lastSX = 0f
    private var lastSY = 0f
    private var edgePeek = true

    private val d get() = resources.displayMetrics.density
    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels
    private val bubblePx get() = (56 * d).toInt()

    private val peekRunnable = Runnable { peek() }
    private val clipRunnable = Runnable { handleClipChange() }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        handler.removeCallbacks(clipRunnable)
        handler.postDelayed(clipRunnable, 150)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val prefs = getSharedPreferences("overlay", MODE_PRIVATE)
        bubbleX = prefs.getInt("x", screenW - bubblePx)
        bubbleY = prefs.getInt("y", 300)

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            NOT_FOCUS_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT }

        owner.start()
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent { OverlayRoot(mode, text, this@OverlayService) }
        }
        applyMode()
        wm.addView(composeView, lp)

        ClipBridge.onText = { onNewText(it) }
        cm.addPrimaryClipChangedListener(clipListener)
        scheduleSnapAndPeek()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::cm.isInitialized) cm.removePrimaryClipChangedListener(clipListener)
        ClipBridge.onText = null
        composeView?.let { runCatching { wm.removeView(it) } }
        composeView = null
        owner.stop()
        super.onDestroy()
    }

    // ---------- Clipboard ----------

    private fun handleClipChange() {
        if (ClipBridge.skipNext) { ClipBridge.skipNext = false; return }
        // Foreground / older Android: direct read may work.
        val direct = try {
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0 && clip.description.hasMimeType("text/*"))
                clip.getItemAt(0).coerceToText(this)?.toString() else null
        } catch (_: Exception) { null }
        if (!direct.isNullOrBlank()) { onNewText(direct); return }
        // Android 10+ background: borrow focus with a transparent activity.
        try {
            startActivity(
                Intent(this, ClipboardReadActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        } catch (_: Exception) { }
    }

    private fun onNewText(t: String) {
        if (t.isBlank() || t == lastText) return
        lastText = t
        text = t
        setMode(OverlayMode.SMART)
    }

    private fun writeClip(t: String) {
        ClipBridge.skipNext = true
        cm.setPrimaryClip(ClipData.newPlainText("smart_clipboard", t))
        lastText = t
    }

    // ---------- Window / modes ----------

    private fun setMode(m: OverlayMode) {
        handler.removeCallbacks(peekRunnable)
        mode = m
        applyMode()
        if (m == OverlayMode.BUBBLE) scheduleSnapAndPeek()
    }

    private fun applyMode() {
        when (mode) {
            OverlayMode.BUBBLE -> {
                lp.flags = NOT_FOCUS_FLAGS
                lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                lp.x = bubbleX; lp.y = bubbleY
            }
            OverlayMode.SMART -> {
                lp.flags = NOT_FOCUS_FLAGS
                lp.x = ((screenW - 300 * d) / 2).toInt()
                lp.y = bubbleY.coerceIn(0, screenH - (200 * d).toInt())
            }
            OverlayMode.EDITOR -> {
                lp.flags = FOCUS_FLAGS
                lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                lp.x = ((screenW - 320 * d) / 2).toInt()
                lp.y = (60 * d).toInt()
            }
        }
        composeView?.let { if (it.isAttachedToWindow) wm.updateViewLayout(it, lp) }
    }

    private fun updateLayout() {
        composeView?.let { if (it.isAttachedToWindow) wm.updateViewLayout(it, lp) }
    }

    private fun animateX(to: Int, onEnd: (() -> Unit)? = null) {
        ValueAnimator.ofInt(lp.x, to).apply {
            duration = 180
            addUpdateListener { lp.x = it.animatedValue as Int; updateLayout() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { onEnd?.invoke() }
            })
            start()
        }
    }

    private fun scheduleSnapAndPeek() {
        handler.removeCallbacks(peekRunnable)
        if (mode != OverlayMode.BUBBLE) return
        val target = if (lp.x + bubblePx / 2 < screenW / 2) 0 else screenW - bubblePx
        animateX(target) {
            bubbleX = target
            getSharedPreferences("overlay", MODE_PRIVATE).edit()
                .putInt("x", bubbleX).putInt("y", bubbleY).apply()
            if (edgePeek) handler.postDelayed(peekRunnable, 2500)
        }
    }

    private fun peek() {
        if (mode != OverlayMode.BUBBLE) return
        val half = bubblePx / 2
        animateX(if (bubbleX <= 0) -half else screenW - half)
    }

    // ---------- OverlayActions ----------

    override fun onDragStart(x: Float, y: Float) {
        handler.removeCallbacks(peekRunnable)
        lastSX = x + lp.x; lastSY = y + lp.y
    }

    override fun onDragMove(x: Float, y: Float) {
        val sx = x + lp.x; val sy = y + lp.y
        lp.x += (sx - lastSX).roundToInt()
        lp.y += (sy - lastSY).roundToInt()
        lastSX = sx; lastSY = sy
        lp.x = lp.x.coerceIn(-bubblePx, screenW)
        lp.y = lp.y.coerceIn(0, screenH - bubblePx)
        if (mode == OverlayMode.BUBBLE) { bubbleX = lp.x; bubbleY = lp.y }
        updateLayout()
    }

    override fun onDragEnd() {
        if (mode == OverlayMode.BUBBLE) scheduleSnapAndPeek()
    }

    override fun openSmart() = setMode(OverlayMode.SMART)
    override fun openEditor() = setMode(OverlayMode.EDITOR)
    override fun minimize() = setMode(OverlayMode.BUBBLE)
    override fun close() { stopSelf() }
    override fun cancelEdit() = setMode(OverlayMode.SMART)

    override fun copy(t: String) {
        writeClip(t)
        Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show()
    }

    override fun confirm(t: String) {
        writeClip(t)
        text = t
        setMode(OverlayMode.SMART)
        Toast.makeText(this, "متن ویرایش‌شده در کلیپ‌بورد قرار گرفت ✓", Toast.LENGTH_SHORT).show()
    }

    // ---------- Foreground notification ----------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("overlay", "کلیپ‌بورد هوشمند", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, "overlay")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("کلیپ‌بورد هوشمند فعال است")
            .setContentText("متن‌های کپی‌شده در حباب شناور نمایش داده می‌شوند")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    companion object {
        private const val BASE = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        private const val NOT_FOCUS_FLAGS = BASE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        private const val FOCUS_FLAGS = BASE

        fun start(ctx: Context) =
            androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, OverlayService::class.java))
    }
}
