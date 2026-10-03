package com.theeclecticwitch.powertothepeople.ui

import com.theeclecticwitch.powertothepeople.officials.JsonFileState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * The reader's own text size, on top of whatever their phone or computer is already set to. Every
 * size in the app is in sp, so one scale reaches everything.
 */
object TextSize {
    val steps = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)
    val names = listOf("Smaller", "Standard", "Large", "Larger", "Largest")

    @Serializable
    data class Prefs(
        val scale: Float = 1f,
        /** The flag behind the app. On unless the reader turns it off. */
        val flag: Boolean = true,
    )

    private val prefs = JsonFileState("display_prefs.json", Prefs.serializer(), Prefs())
    val flow: StateFlow<Prefs> = prefs.flow

    fun set(scale: Float) = prefs.update { it.copy(scale = scale) }

    fun setFlag(on: Boolean) = prefs.update { it.copy(flag = on) }

    fun nameOf(scale: Float): String = names[steps.indexOfFirst { it >= scale - 0.01f }.coerceAtLeast(0)]
}
