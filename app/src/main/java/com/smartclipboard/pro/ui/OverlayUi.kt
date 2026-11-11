package com.smartclipboard.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartclipboard.pro.overlay.OverlayMode

interface OverlayActions {
    fun onDragStart(x: Float, y: Float)
    fun onDragMove(x: Float, y: Float)
    fun onDragEnd()
    fun openSmart()
    fun openEditor()
    fun minimize()
    fun close()
    fun copy(t: String)
    fun confirm(t: String)
    fun cancelEdit()
}

@Composable
fun OverlayRoot(mode: OverlayMode, text: String, actions: OverlayActions) {
    SmartClipTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            when (mode) {
                OverlayMode.BUBBLE -> Bubble(actions)
                OverlayMode.SMART -> SmartWindow(text, actions)
                OverlayMode.EDITOR -> EditorWindow(text, actions)
            }
        }
    }
}

private fun Modifier.dragWindow(a: OverlayActions) = pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { a.onDragStart(it.x, it.y) },
        onDragEnd = { a.onDragEnd() },
        onDragCancel = { a.onDragEnd() }
    ) { change, _ ->
        change.consume()
        a.onDragMove(change.position.x, change.position.y)
    }
}

@Composable
private fun Bubble(a: OverlayActions) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .semantics { contentDescription = "حباب کلیپ‌بورد هوشمند. برای باز کردن لمس کنید" }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { a.openSmart() },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = !menu
                        }
                    )
                }
                .dragWindow(a),
            contentAlignment = Alignment.Center
        ) { Text("📋", fontSize = 24.sp) }
        if (menu) {
            Spacer(Modifier.height(4.dp))
            AssistChip(onClick = { a.close() }, label = { Text("بستن") })
        }
    }
}

@Composable
private fun SmartWindow(text: String, a: OverlayActions) {
    Card(
        Modifier.width(300.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().dragWindow(a),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📋 کلیپ‌بورد هوشمند", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(
                    onClick = { a.minimize() },
                    modifier = Modifier.semantics { contentDescription = "کوچک کردن" }
                ) { Text("—") }
                TextButton(
                    onClick = { a.close() },
                    modifier = Modifier.semantics { contentDescription = "بستن" }
                ) { Text("✕") }
            }
            Spacer(Modifier.height(6.dp))
            Text(text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { a.openEditor() }, Modifier.weight(1f)) { Text("ویرایش") }
                Button(onClick = { a.copy(text) }, Modifier.weight(1f)) { Text("کپی") }
            }
        }
    }
}

@Composable
private fun EditorWindow(initial: String, a: OverlayActions) {
    var value by remember { mutableStateOf(initial) }
    val undo = remember { mutableStateListOf<String>() }
    val redo = remember { mutableStateListOf<String>() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Card(
        Modifier.width(320.dp).height(380.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth().dragWindow(a),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { a.confirm(value) },
                    modifier = Modifier.semantics { contentDescription = "تأیید" }) { Text("✓") }
                TextButton(onClick = { a.cancelEdit() },
                    modifier = Modifier.semantics { contentDescription = "لغو" }) { Text("✕") }
                TextButton(onClick = { a.copy(value) }) { Text("کپی") }
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = undo.isNotEmpty(),
                    onClick = { redo.add(value); value = undo.removeAt(undo.lastIndex) },
                    modifier = Modifier.semantics { contentDescription = "واگرد" }
                ) { Text("↶") }
                TextButton(
                    enabled = redo.isNotEmpty(),
                    onClick = { undo.add(value); value = redo.removeAt(redo.lastIndex) },
                    modifier = Modifier.semantics { contentDescription = "ازنو" }
                ) { Text("↷") }
            }
            OutlinedTextField(
                value = value,
                onValueChange = { n -> if (n != value) { undo.add(value); redo.clear(); value = n } },
                modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(focus)
            )
            Spacer(Modifier.height(6.dp))
            val words = value.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            Text(
                "کاراکتر: ${value.length}   کلمه: $words   خط: ${value.lines().size}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
