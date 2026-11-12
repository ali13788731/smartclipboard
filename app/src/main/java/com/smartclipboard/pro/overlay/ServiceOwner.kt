package com.smartclipboard.pro.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/** Lets a ComposeView live inside a Service window (no Activity). */
class ServiceOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun start() {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        // INITIALIZED -> DESTROYED throws, so only destroy if we were ever started.
        if (registry.currentState != Lifecycle.State.INITIALIZED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
        viewModelStore.clear()
    }
}
