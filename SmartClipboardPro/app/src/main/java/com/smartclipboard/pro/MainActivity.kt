package com.smartclipboard.pro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smartclipboard.pro.overlay.OverlayService
import com.smartclipboard.pro.overlay.ServiceState
import com.smartclipboard.pro.ui.SmartClipTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SmartClipTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { HomeScreen() }
                }
            }
        }
    }
}

private fun notifGranted(ctx: Context) =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun openOverlaySettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var notifOk by remember { mutableStateOf(notifGranted(ctx)) }
    var askedNotif by remember { mutableStateOf(false) }
    var showPermDialog by remember { mutableStateOf(false) }
    var tipsOpen by remember { mutableStateOf(false) }
    var startTick by remember { mutableStateOf(0) }
    var startFailed by remember { mutableStateOf(false) }
    var appear by remember { mutableStateOf(false) }
    val running = ServiceState.running

    LaunchedEffect(Unit) { appear = true }
    LaunchedEffect(startTick) {
        if (startTick > 0) {
            startFailed = false
            delay(2500)
            startFailed = !ServiceState.running
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                overlayOk = Settings.canDrawOverlays(ctx)
                notifOk = notifGranted(ctx)
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifOk = granted
        startTick++
        OverlayService.start(ctx)   // the service works even if notifications are denied
    }

    fun activate() {
        if (!overlayOk) { showPermDialog = true; return }
        if (Build.VERSION.SDK_INT >= 33 && !notifOk && !askedNotif) {
            askedNotif = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startTick++
        OverlayService.start(ctx)
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(cs.background, cs.primaryContainer.copy(alpha = 0.25f), cs.background))
        )
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Reveal(0, appear) { Header() }

            Reveal(1, appear) {
                HeroCard(
                    running = running,
                    startFailed = startFailed,
                    error = ServiceState.lastError,
                    onToggle = {
                        if (running) { startFailed = false; OverlayService.stop(ctx) } else activate()
                    }
                )
            }

            Reveal(2, appear) {
                Panel {
                    Text("مجوزهای لازم", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Spacer(Modifier.height(10.dp))
                    PermRow(
                        title = "نمایش روی سایر برنامه‌ها",
                        desc = "برای نمایش کارت متن کپی‌شده بالای هر برنامه",
                        ok = overlayOk,
                        action = "اعطا",
                        onClick = { openOverlaySettings(ctx) }
                    )
                    if (Build.VERSION.SDK_INT >= 33) {
                        Spacer(Modifier.height(10.dp))
                        PermRow(
                            title = "اعلان‌ها",
                            desc = "برای نشان دادن وضعیت فعال بودن سرویس",
                            ok = notifOk,
                            action = "اعطا",
                            onClick = { askedNotif = true; notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }
                }
            }

            Reveal(3, appear) {
                Panel {
                    Text("آزمایش", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "اول مطمئن شوید پنجره روی صفحه نمایش داده می‌شود:",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { if (overlayOk) OverlayService.demo(ctx) else showPermDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("نمایش پنجره‌ی آزمایشی") }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(cs.surfaceVariant.copy(alpha = 0.55f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (running) cs.secondary else cs.outline)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            ServiceState.lastEvent,
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                }
            }

            Reveal(4, appear) {
                Panel(Modifier.animateContentSize(tween(300))) {
                    val rot by animateFloatAsState(if (tipsOpen) 180f else 0f, tween(300), label = "arrow")
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { tipsOpen = !tipsOpen },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "نکات سازگاری با گوشی‌ها",
                            style = MaterialTheme.typography.titleMedium, color = cs.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = cs.onSurfaceVariant, modifier = Modifier.rotate(rot))
                    }
                    AnimatedVisibility(tipsOpen) {
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tip("شیائومی/ردمی/پوکو: Autostart را روشن کنید و Battery saver برنامه را «بدون محدودیت» بگذارید. در بخش مجوزهای برنامه، «نمایش پنجره‌ها هنگام اجرا در پس‌زمینه» را هم روشن کنید.")
                            Tip("سامسونگ: برنامه را از Sleeping apps خارج کنید.")
                            Tip("هواوی/آنر: App launch را روی «مدیریت دستی» بگذارید و همه‌ی گزینه‌ها را روشن کنید.")
                            Tip("اپو/ریلمی/وان‌پلاس: Auto-launch و Background activity را فعال کنید.")
                            Tip("اندروید ۱۰ به بعد: خواندن متن کپی‌شده فقط با گرفتن فوکوس لحظه‌ای ممکن است؛ برنامه این کار را با یک پنجره‌ی نامرئی یک‌پیکسلی انجام می‌دهد. ممکن است کیبورد برای یک لحظه بپرد؛ عادی است.")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            title = { Text("مجوز لازم است") },
            text = {
                Text("برای نمایش کارت متن کپی‌شده روی سایر برنامه‌ها باید مجوز «نمایش روی سایر برنامه‌ها» را بدهید. در صفحه‌ی بعد، کلید کنار «کلیپ‌بورد هوشمند» را روشن کنید و برگردید.")
            },
            confirmButton = {
                TextButton(onClick = { showPermDialog = false; openOverlaySettings(ctx) }) { Text("باز کردن تنظیمات") }
            },
            dismissButton = { TextButton(onClick = { showPermDialog = false }) { Text("بعداً") } }
        )
    }
}

@Composable
private fun Reveal(index: Int, visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(480, delayMillis = index * 90)) +
            slideInVertically(tween(480, delayMillis = index * 90)) { it / 8 }
    ) { content() }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(cs.surface)
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), shape).padding(18.dp),
        content = content
    )
}

@Composable
private fun Header() {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(cs.primary, cs.secondary))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("کلیپ‌بورد هوشمند", style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
            Text(
                "کپی کن، ویرایش کن، همیشه آماده باش",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusOrb(active: Boolean) {
    val cs = MaterialTheme.colorScheme
    val inf = rememberInfiniteTransition(label = "pulse")
    val p by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
    val core = if (active) Color.White else cs.primary
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r0 = size.minDimension / 4f
            if (active) {
                for (i in 0..1) {
                    val q = (p + i * 0.5f) % 1f
                    drawCircle(Color.White.copy(alpha = 0.38f * (1f - q)), radius = r0 + q * r0 * 1.15f)
                }
            }
            drawCircle(core.copy(alpha = 0.16f), radius = r0 * 1.28f)
            drawCircle(core, radius = r0)
        }
        Icon(
            if (active) Icons.Rounded.Check else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = if (active) cs.primary else cs.onPrimary,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun HeroCard(running: Boolean, startFailed: Boolean, error: String?, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val c1 by animateColorAsState(if (running) cs.primary else cs.surface, tween(500), label = "c1")
    val c2 by animateColorAsState(if (running) cs.secondary else cs.surface, tween(500), label = "c2")
    val fg by animateColorAsState(if (running) Color.White else cs.onSurface, tween(500), label = "fg")
    val shape = RoundedCornerShape(30.dp)

    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .border(1.dp, cs.outlineVariant.copy(alpha = if (running) 0f else 0.6f), shape)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusOrb(running)
        Spacer(Modifier.height(10.dp))
        AnimatedContent(targetState = running, label = "title") { r ->
            Text(
                if (r) "فعال است و کلیپ‌بورد را زیر نظر دارد" else "غیرفعال",
                style = MaterialTheme.typography.titleLarge, color = fg
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (running) "حالا از هر برنامه‌ای متنی را کپی کنید؛ کارت ویرایش پایین سمت راست ظاهر می‌شود."
            else "برای شروع، دکمه‌ی زیر را بزنید.",
            style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        val problem = error ?: if (startFailed)
            "سرویس شروع نشد. محدودیت باتری یا Autostart گوشی را بررسی کنید (نکات سازگاری پایین صفحه)." else null
        if (problem != null && !running) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(cs.error.copy(alpha = 0.12f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, null, tint = cs.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(problem, style = MaterialTheme.typography.bodySmall, color = cs.error)
            }
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = if (running) ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = cs.primary)
            else ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary)
        ) {
            AnimatedContent(targetState = running, label = "btn") { r ->
                Text(
                    if (r) "غیرفعال‌سازی" else "فعال‌سازی حباب کلیپ‌بورد",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean, action: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val badge by animateColorAsState(if (ok) cs.secondary else cs.error.copy(alpha = 0.85f), tween(400), label = "badge")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            Icon(
                if (ok) Icons.Rounded.Check else Icons.Rounded.Warning, null,
                tint = Color.White, modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (!ok) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) { Text(action) }
        }
    }
}

@Composable
private fun Tip(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(cs.secondary))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}
