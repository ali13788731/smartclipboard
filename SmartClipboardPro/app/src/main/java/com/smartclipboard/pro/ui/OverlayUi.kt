package com.smartclipboard.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartclipboard.pro.overlay.OverlayMode

interface OverlayActions {
    fun openEditor()
    fun dismiss()
    fun copy(t: String)
    fun confirm(t: String)
    fun cancelEdit()
}

@Composable
fun OverlayRoot(mode: OverlayMode, text: String, actions: OverlayActions) {
    SmartClipTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            when (mode) {
                OverlayMode.BUBBLE -> Box(Modifier.size(1.dp))
                OverlayMode.SMART -> PreviewChip(text, actions)
                OverlayMode.EDITOR -> EditorScreen(text, actions)
            }
        }
    }
}

@Composable
private fun PreviewChip(text: String, a: OverlayActions) {
    Box(
        Modifier
            .width(160.dp)
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(3.dp)
            .background(Color(0xFFEDEDED), RoundedCornerShape(15.dp))
            .clickable { a.openEditor() }
            .semantics { contentDescription = "متن کپی‌شده. برای ویرایش لمس کنید" }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFF222222),
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun EditorScreen(initial: String, a: OverlayActions) {
    var value by remember { mutableStateOf(initial) }
    val undo = remember { mutableStateListOf<String>() }
    val redo = remember { mutableStateListOf<String>() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { a.cancelEdit() },
                    modifier = Modifier.semantics { contentDescription = "لغو" }
                ) { Text("✕", fontSize = 22.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("ویرایش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text("از کلیپ‌بورد", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(
                    onClick = { a.confirm(value) },
                    modifier = Modifier.semantics { contentDescription = "تأیید" }
                ) { Text("✓", fontSize = 24.sp) }
            }

            OutlinedTextField(
                value = value,
                onValueChange = { n -> if (n != value) { undo.add(value); redo.clear(); value = n } },
                modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(focus),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 30.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                TextButton(onClick = { a.copy(value) }) { Text("کپی") }
                Spacer(Modifier.weight(1f))
                val words = value.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                Text(
                    "کاراکتر: ${value.length}   کلمه: $words",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
