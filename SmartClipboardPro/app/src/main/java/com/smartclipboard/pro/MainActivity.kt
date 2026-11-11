package com.smartclipboard.pro

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.smartclipboard.pro.overlay.OverlayService
import com.smartclipboard.pro.ui.SmartClipTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartClipTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) { HomeScreen() }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) overlayOk = Settings.canDrawOverlays(ctx)
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("کلیپ‌بورد هوشمند", style = MaterialTheme.typography.headlineMedium)
        Text("کپی کن، ویرایش کن، همیشه آماده باش!", style = MaterialTheme.typography.bodyLarge)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("وضعیت مجوزها", style = MaterialTheme.typography.titleMedium)
                Text(if (overlayOk) "✓ نمایش روی سایر برنامه‌ها: فعال" else "✕ نمایش روی سایر برنامه‌ها: غیرفعال")
                if (!overlayOk) {
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        )
                    }) { Text("اعطای مجوز Overlay") }
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("اجازه‌ی اعلان")
                    }
                }
            }
        }

        Button(
            enabled = overlayOk,
            onClick = { OverlayService.start(ctx) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("فعال‌سازی حباب کلیپ‌بورد") }
        OutlinedButton(onClick = { OverlayService.stop(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Text("غیرفعال‌سازی")
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("نکات سازگاری", style = MaterialTheme.typography.titleMedium)
                Text("• شیائومی/ردمی/پوکو: Autostart را روشن و Battery saver برنامه را «بدون محدودیت» کنید.")
                Text("• سامسونگ: برنامه را از Sleeping apps خارج کنید.")
                Text("• هواوی/آنر: App launch را روی «مدیریت دستی» بگذارید و همه گزینه‌ها را روشن کنید.")
                Text("• اپو/ریلمی: Auto-launch و Background activity را فعال کنید.")
                Text("• اندروید ۱۰+: متن کپی‌شده با باز و بسته شدن لحظه‌ای یک صفحه‌ی شفاف خوانده می‌شود؛ این رفتار عادی است.")
            }
        }
    }
}
