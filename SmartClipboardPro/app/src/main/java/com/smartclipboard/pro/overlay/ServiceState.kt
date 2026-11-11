package com.smartclipboard.pro.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observable state shared between the service and the app screen (same process). */
object ServiceState {
    var running by mutableStateOf(false)
    var lastEvent by mutableStateOf("هنوز متنی شناسایی نشده است")
    var lastError by mutableStateOf<String?>(null)
}
