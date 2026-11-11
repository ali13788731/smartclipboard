package com.smartclipboard.pro.overlay

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

/** BUBBLE = idle (invisible 1dp window), SMART = preview chip bottom-right, EDITOR = full-screen editor. */
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
    private var lastTime = 0L

    private val d get() = resources.displayMetrics.density

    private val hideRunnable = Runnable { if (mode == OverlayMode.SMART) changeMode(OverlayMode.BUBBLE) }
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

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            IDLE_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.RIGHT }

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
        val direct = try {
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0 && clip.description.hasMimeType("text/*"))
                clip.getItemAt(0).coerceToText(this)?.toString() else null
        } catch (_: Exception) { null }
        if (!direct.isNullOrBlank()) { onNewText(direct); return }
        try {
            startActivity(
                Intent(this, ClipboardReadActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        } catch (_: Exception) { }
    }

    private fun onNewText(t: String) {
        if (t.isBlank()) return
        if (mode == OverlayMode.EDITOR) return
        val now = System.currentTimeMillis()
        if (t == lastText && now - lastTime < 800) return
        lastText = t
        lastTime = now
        text = t
        changeMode(OverlayMode.SMART)
    }

    private fun writeClip(t: String) {
        ClipBridge.skipNext = true
        handler.postDelayed({ ClipBridge.skipNext = false }, 1000)
        cm.setPrimaryClip(ClipData.newPlainText("smart_clipboard", t))
        lastText = t
        lastTime = System.currentTimeMillis()
    }

    // ---------- Window / modes ----------

    private fun changeMode(m: OverlayMode) {
        handler.removeCallbacks(hideRunnable)
        mode = m
        applyMode()
        if (m == OverlayMode.SMART) handler.postDelayed(hideRunnable, 10_000)
    }

    private fun applyMode() {
        when (mode) {
            OverlayMode.BUBBLE -> {
                lp.width = WindowManager.LayoutParams.WRAP_CONTENT
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                lp.gravity = Gravity.BOTTOM or Gravity.RIGHT
                lp.flags = IDLE_FLAGS
                lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                lp.x = 0; lp.y = 0
            }
            OverlayMode.SMART -> {
                lp.width = WindowManager.LayoutParams.WRAP_CONTENT
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                lp.gravity = Gravity.BOTTOM or Gravity.RIGHT
                lp.flags = IDLE_FLAGS
                lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                lp.x = (16 * d).toInt()
                lp.y = (110 * d).toInt()
            }
            OverlayMode.EDITOR -> {
                lp.width = WindowManager.LayoutParams.MATCH_PARENT
                lp.height = WindowManager.LayoutParams.MATCH_PARENT
                lp.gravity = Gravity.TOP or Gravity.LEFT
                lp.flags = EDITOR_FLAGS
                lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                lp.x = 0; lp.y = 0
            }
        }
        composeView?.let { if (it.isAttachedToWindow) wm.updateViewLayout(it, lp) }
    }

    // ---------- OverlayActions ----------

    override fun openEditor() = changeMode(OverlayMode.EDITOR)
    override fun dismiss() = changeMode(OverlayMode.BUBBLE)
    override fun cancelEdit() = changeMode(OverlayMode.BUBBLE)

    override fun copy(t: String) {
        writeClip(t)
        Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show()
    }

    override fun confirm(t: String) {
        writeClip(t)
        text = t
        changeMode(OverlayMode.BUBBLE)
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
            .setContentText("متن‌های کپی‌شده پایین سمت راست نمایش داده می‌شوند")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    companion object {
        private const val IDLE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        private const val EDITOR_FLAGS = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        fun start(ctx: Context) =
            androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, OverlayService::class.java))
    }
}
