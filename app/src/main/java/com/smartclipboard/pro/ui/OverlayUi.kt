package com.smartclipboard.pro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartclipboard.pro.overlay.OverlayMode
import com.smartclipboard.pro.overlay.OverlayService
import kotlinx.coroutines.delay
import kotlin.math.abs

interface OverlayActions {
    fun openEditor()
    fun dismiss()
    fun copy(t: String)
    fun confirm(t: String)
    fun cancelEdit()
}

@Composable
fun OverlayRoot(mode: OverlayMode, shown: Boolean, text: String, token: Int, actions: OverlayActions) {
    SmartClipTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            when (mode) {
                OverlayMode.NONE -> Box(Modifier.size(1.dp))
                OverlayMode.SMART -> PreviewChip(shown, text, token, actions)
                OverlayMode.EDITOR -> EditorScreen(shown, text, actions)
            }
        }
    }
}

// ------------------------------------------------------------------ preview card

@Composable
private fun PreviewChip(shown: Boolean, text: String, token: Int, a: OverlayActions) {
    val vis = remember { MutableTransitionState(false) }
    vis.targetState = shown
    AnimatedVisibility(
        visibleState = vis,
        enter = fadeIn(tween(260)) +
            scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.82f, transformOrigin = TransformOrigin(1f, 1f)) +
            slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 3 },
        exit = fadeOut(tween(200)) +
            scaleOut(tween(240), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 1f))
    ) {
        ChipCard(text, token, a)
    }
}

@Composable
private fun ChipCard(text: String, token: Int, a: OverlayActions) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    var dx by remember { mutableStateOf(0f) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(token) {
        progress.snapTo(1f)
        progress.animateTo(0f, tween(OverlayService.CHIP_MS.toInt(), easing = LinearEasing))
    }

    // outer padding gives the shadow and the slide animation room inside the wrap-content window
    Box(Modifier.padding(start = 18.dp, end = 14.dp, top = 18.dp, bottom = 18.dp)) {
        Column(
            Modifier
                .width(304.dp)
                .graphicsLayer {
                    translationX = dx
                    alpha = 1f - (abs(dx) / 700f).coerceIn(0f, 0.8f)
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (abs(dx) > 150f) a.dismiss() else dx = 0f },
                        onDragCancel = { dx = 0f }
                    ) { _, delta -> dx += delta }
                }
                .shadow(16.dp, shape, ambientColor = cs.primary, spotColor = cs.primary)
                .clip(shape)
                .background(cs.surface)
                .clickable(onClickLabel = "ویرایش متن") { a.openEditor() }
        ) {
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .background(Brush.horizontalGradient(listOf(cs.primary, cs.secondary)))
            )
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(cs.secondary))
                    Spacer(Modifier.width(8.dp))
                    Text("متن کپی شد", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { a.dismiss() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "بستن", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text.take(400),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = cs.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(cs.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Edit, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ویرایش", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${text.length} کاراکتر", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
            }
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = cs.secondary,
                trackColor = Color.Transparent
            )
        }
    }
}

// ------------------------------------------------------------------ editor

private fun persianize(s: String) =
    s.replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')

private fun toEnglishDigits(s: String) = buildString {
    for (c in s) append(
        when (c) {
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            else -> c
        }
    )
}

private fun toPersianDigits(s: String) = buildString {
    for (c in s) append(
        when (c) {
            in '0'..'9' -> '۰' + (c - '0')
            in '٠'..'٩' -> '۰' + (c - '٠')
            else -> c
        }
    )
}

private fun tidySpaces(s: String) =
    s.lines().joinToString("\n") { it.trim().replace(Regex("[ \\t]{2,}"), " ") }
        .replace(Regex("\\n{3,}"), "\n\n").trim()

@Composable
private fun Pill(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceVariant)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
    }
}

@Composable
private fun EditorScreen(shown: Boolean, initial: String, a: OverlayActions) {
    val cs = MaterialTheme.colorScheme
    var value by remember(initial) { mutableStateOf(initial) }
    val undo = remember(initial) { mutableStateListOf<String>() }
    val redo = remember(initial) { mutableStateListOf<String>() }
    var lastEdit by remember(initial) { mutableStateOf(0L) }
    val focus = remember { FocusRequester() }

    val vis = remember { MutableTransitionState(false) }
    vis.targetState = shown
    val scrim = remember { Animatable(0f) }
    LaunchedEffect(shown) { scrim.animateTo(if (shown) 0.55f else 0f, tween(300)) }
    LaunchedEffect(Unit) {
        delay(380)
        runCatching { focus.requestFocus() }
    }

    fun change(n: String, force: Boolean = false) {
        if (n == value) return
        val now = System.currentTimeMillis()
        // typing bursts count as one undo step
        if (force || undo.isEmpty() || now - lastEdit > 800) undo.add(value)
        lastEdit = now
        redo.clear()
        value = n
    }

    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim.value))
            .clickable(interactionSource = noRipple, indication = null) { a.cancelEdit() }
            .onPreviewKeyEvent {
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) { a.cancelEdit(); true } else false
            }
    ) {
        AnimatedVisibility(
            visibleState = vis,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.93f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                color = cs.surface,
                shadowElevation = 12.dp
            ) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Box(
                        Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally)
                            .size(width = 42.dp, height = 4.dp).clip(RoundedCornerShape(50))
                            .background(cs.outlineVariant)
                    )
                    Row(Modifier.padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(cs.primary, cs.secondary))),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ویرایش متن", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            val words = value.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                            Text(
                                "${value.length} کاراکتر  ·  $words کلمه",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { a.cancelEdit() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "لغو", tint = cs.onSurfaceVariant)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Pill("فاصله‌های اضافه") { change(tidySpaces(value), true) }
                        Pill("ی و ک فارسی") { change(persianize(value), true) }
                        Pill("ارقام انگلیسی") { change(toEnglishDigits(value), true) }
                        Pill("ارقام فارسی") { change(toPersianDigits(value), true) }
                        Pill("پاک کردن همه") { change("", true) }
                    }

                    Box(
                        Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(cs.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        TextField(
                            value = value,
                            onValueChange = { change(it) },
                            modifier = Modifier.fillMaxSize().focusRequester(focus),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 30.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("↶ واگرد", enabled = undo.isNotEmpty()) {
                            redo.add(value); value = undo.removeAt(undo.lastIndex)
                        }
                        Pill("↷ ازنو", enabled = redo.isNotEmpty()) {
                            undo.add(value); value = redo.removeAt(redo.lastIndex)
                        }
                        Pill("کپی") { a.copy(value) }
                    }
                    Button(
                        onClick = { a.confirm(value) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تأیید و جایگزینی در کلیپ‌بورد", style = MaterialTheme.typography.labelLarge, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
