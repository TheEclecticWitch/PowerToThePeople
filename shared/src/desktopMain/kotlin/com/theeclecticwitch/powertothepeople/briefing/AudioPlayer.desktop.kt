package com.theeclecticwitch.powertothepeople.briefing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Java has no MP3 player of its own, so on a computer the card opens the recording in the browser. */
actual object AudioPlayer {
    actual val playsInApp = false
    actual val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState())
    actual fun play(url: String) {}
    actual fun pause() {}
}
