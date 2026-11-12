package com.smartclipboard.pro.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.smartclipboard.pro.MainActivity
import com.smartclipboard.pro.R
import com.smartclipboard.pro.ui.OverlayActions
import com.smartclipboard.pro.ui.OverlayRoot

/** NONE = no window at all, SMART = preview card (bottom corner), EDITOR = full-screen editor sheet. */
enum class OverlayMode { NONE, SMART, EDITOR }

/** 1px focusable overlay window. Holding window focus is what allows reading the clipboard on Android 10+. */
private class ProbeView(ctx: Context, private val onFocused: () -> Unit) : View(ctx) {
    private var fired = false

    init { setBackgroundColor(Color.argb(1, 0, 0, 0)) }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && !fired) {
            fired = true
            onFocused()
        }
    }
}

class OverlayService : Service(), OverlayActions {

    private lateinit var wm: WindowManager
    private lateinit var cm: ClipboardManager
    private val owner = ServiceOwner()
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false

    private var composeView: ComposeView? = null
    private var composeLp: WindowManager.LayoutParams? = null
    private var probe: ProbeView? = null

    private var mode by mutableStateOf(OverlayMode.NONE)
    private var shown by mutableStateOf(false)
    private var text by mutableStateOf("")
    private var token by mutableStateOf(0)

    private var lastText: String? = null
    private var lastTime = 0L
    private var viaA11y = false

    private val d get() = resources.displayMetrics.density

    private val hideRunnable = Runnable { hideUi() }
    private val removeRunnable = Runnable { if (!shown) removeView() }
    private val clipRunnable = Runnable { handleClipChange() }
    private val probeTimeout = Runnable {
        if (probe != null) {
            removeProbe()
            launchReaderActivity()
        }
    }
    private val failRunnable = Runnable {
        ServiceState.lastEvent = "کپی شناسایی شد ولی سیستم اجازه‌ی خواندن متن را نداد"
    }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        handler.removeCallbacks(clipRunnable)
        handler.postDelayed(clipRunnable, 120)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            ServiceState.lastError = "مجوز «نمایش روی سایر برنامه‌ها» داده نشده است"
            stopSelf()
            return
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        owner.start()
        ClipBridge.onText = { onNewText(it, "صفحه‌ی شفاف") }
        ClipBridge.requestRead = { handler.postDelayed({ requestedRead() }, 300) }
        cm.addPrimaryClipChangedListener(clipListener)
        ready = true
        ServiceState.lastError = null
        ServiceState.running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ready) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DEMO -> showChip(DEMO_TEXT)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (ready) {
            runCatching { cm.removePrimaryClipChangedListener(clipListener) }
            removeProbe()
            removeView()
        }
        ready = false
        ClipBridge.onText = null
        ClipBridge.requestRead = null
        ServiceState.running = false
        owner.stop()
        super.onDestroy()
    }

    // ---------- Clipboard ----------

    private fun readClip(): String? = try {
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0 && clip.description.hasMimeType("text/*"))
            clip.getItemAt(0).coerceToText(this)?.toString() else null
    } catch (e: Throwable) {
        null
    }

    private fun handleClipChange() {
        if (ClipBridge.skipNext) { ClipBridge.skipNext = false; return }
        if (mode == OverlayMode.EDITOR) return
        viaA11y = false
        // 1) works when we happen to have focus (or on Android 9 and below)
        val direct = readClip()
        if (!direct.isNullOrBlank()) { onNewText(direct, "مستقیم"); return }
        // 2) Android 10+: grab input focus with a 1px overlay window, then read
        readViaFocusWindow()
    }

    /** Triggered by the accessibility service right after the user pressed a "copy" button somewhere. */
    private fun requestedRead() {
        if (!ready || mode == OverlayMode.EDITOR) return
        viaA11y = true
        val direct = readClip()
        if (!direct.isNullOrBlank()) { onNewText(direct, "تشخیص دکمه‌ی کپی"); return }
        readViaFocusWindow()
    }

    private fun readViaFocusWindow() {
        if (probe != null) return
        val v = ProbeView(this) {
            handler.postDelayed({
                if (probe == null) return@postDelayed
                val t = readClip()
                removeProbe()
                if (!t.isNullOrBlank()) onNewText(t, "پنجره‌ی کمکی") else launchReaderActivity()
            }, 40)
        }
        val p = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT }
        try {
            wm.addView(v, p)
            probe = v
            handler.postDelayed(probeTimeout, 800)
        } catch (e: Exception) {
            launchReaderActivity()
        }
    }

    private fun removeProbe() {
        handler.removeCallbacks(probeTimeout)
        probe?.let { runCatching { wm.removeView(it) } }
        probe = null
    }

    /** Last resort: a transparent activity that briefly takes focus. */
    private fun launchReaderActivity() {
        handler.removeCallbacks(failRunnable)
        handler.postDelayed(failRunnable, 2500)
        try {
            startActivity(
                Intent(this, ClipboardReadActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        } catch (e: Exception) {
            handler.removeCallbacks(failRunnable)
            failRunnable.run()
        }
    }

    private fun onNewText(t: String, method: String) {
        handler.removeCallbacks(failRunnable)
        val window = if (viaA11y) 6000L else 800L
        viaA11y = false
        if (t.isBlank() || mode == OverlayMode.EDITOR) return
        val now = System.currentTimeMillis()
        if (t == lastText && now - lastTime < window) return
        lastText = t
        lastTime = now
        ServiceState.lastEvent = "آخرین کپی: «${t.take(36).replace('\n', ' ')}» — روش: $method"
        showChip(t)
    }

    private fun writeClip(t: String) {
        ClipBridge.skipNext = true
        handler.postDelayed({ ClipBridge.skipNext = false }, 1000)
        cm.setPrimaryClip(ClipData.newPlainText("smart_clipboard", t))
        lastText = t
        lastTime = System.currentTimeMillis()
    }

    // ---------- Window / modes ----------

    private fun configure(lp: WindowManager.LayoutParams) {
        if (mode == OverlayMode.EDITOR) {
            lp.width = WindowManager.LayoutParams.MATCH_PARENT
            lp.height = WindowManager.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.TOP or Gravity.LEFT
            lp.flags = EDITOR_FLAGS
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            lp.x = 0; lp.y = 0
        } else {
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            lp.gravity = Gravity.BOTTOM or Gravity.RIGHT
            lp.flags = CHIP_FLAGS
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            lp.x = 0
            lp.y = (72 * d).toInt()
        }
    }

    private fun ensureView(): Boolean {
        val existing = composeView
        val existingLp = composeLp
        if (existing != null && existingLp != null) {
            configure(existingLp)
            runCatching { wm.updateViewLayout(existing, existingLp) }
            return true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            CHIP_FLAGS,
            PixelFormat.TRANSLUCENT
        )
        configure(lp)
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent { OverlayRoot(mode, shown, text, token, this@OverlayService) }
        }
        return try {
            wm.addView(v, lp)
            composeView = v
            composeLp = lp
            true
        } catch (e: Exception) {
            ServiceState.lastError = "نمایش پنجره ناموفق بود: ${e.javaClass.simpleName}"
            false
        }
    }

    private fun showChip(t: String) {
        handler.removeCallbacks(removeRunnable)
        handler.removeCallbacks(hideRunnable)
        text = t
        token += 1
        mode = OverlayMode.SMART
        if (!ensureView()) return
        shown = true
        handler.postDelayed(hideRunnable, CHIP_MS)
    }

    private fun hideUi() {
        handler.removeCallbacks(hideRunnable)
        shown = false
        handler.removeCallbacks(removeRunnable)
        handler.postDelayed(removeRunnable, 340)
    }

    private fun removeView() {
        composeView?.let { runCatching { wm.removeView(it) } }
        composeView = null
        composeLp = null
        shown = false
        mode = OverlayMode.NONE
    }

    // ---------- OverlayActions ----------

    override fun openEditor() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(removeRunnable)
        mode = OverlayMode.EDITOR
        if (ensureView()) shown = true
    }

    override fun dismiss() = hideUi()
    override fun cancelEdit() = hideUi()

    override fun copy(t: String) {
        writeClip(t)
        Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show()
    }

    override fun confirm(t: String) {
        writeClip(t)
        text = t
        hideUi()
        Toast.makeText(this, "متن ویرایش‌شده در کلیپ‌بورد قرار گرفت ✓", Toast.LENGTH_SHORT).show()
    }

    // ---------- Foreground notification ----------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("overlay", "کلیپ‌بورد هوشمند", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, "overlay")
            .setSmallIcon(R.drawable.ic_stat_clip)
            .setContentTitle("کلیپ‌بورد هوشمند فعال است")
            .setContentText("هر متنی که کپی کنید همین‌جا آماده‌ی ویرایش می‌شود")
            .setContentIntent(open)
            .addAction(0, "غیرفعال‌سازی", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    companion object {
        const val CHIP_MS = 7000L
        private const val ACTION_DEMO = "com.smartclipboard.pro.DEMO"
        private const val ACTION_STOP = "com.smartclipboard.pro.STOP"
        private const val DEMO_TEXT =
            "این یک متن آزمایشی است. اگر این کارت را می‌بینید، نمایش روی برنامه‌ها درست کار می‌کند. برای ویرایش، همین‌جا را لمس کنید."

        private const val CHIP_FLAGS = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        private const val EDITOR_FLAGS = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))

        fun demo(ctx: Context) =
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, OverlayService::class.java).setAction(ACTION_DEMO)
            )

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, OverlayService::class.java))
    }
}
