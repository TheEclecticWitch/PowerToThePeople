package com.theeclecticwitch.powertothepeople.briefing

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

actual object AudioPlayer {
    actual val playsInApp = true
    private val _state = MutableStateFlow(PlayerState())
    actual val state: StateFlow<PlayerState> = _state
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    actual fun play(url: String) {
        val current = player
        if (current != null && _state.value.url == url) {
            current.start()
            _state.value = _state.value.copy(playing = true)
            tick()
            return
        }
        release()
        _state.value = PlayerState(url = url, playing = true)
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setDataSource(url)
            setOnPreparedListener {
                it.start()
                _state.value = _state.value.copy(durationMs = it.duration.toLong())
                tick()
            }
            setOnCompletionListener {
                _state.value = _state.value.copy(playing = false, positionMs = 0)
                ticker?.cancel()
            }
            setOnErrorListener { _, _, _ ->
                _state.value = PlayerState()
                true
            }
            prepareAsync()
        }
    }

    actual fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        _state.value = _state.value.copy(playing = false)
        ticker?.cancel()
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                player?.let { _state.value = _state.value.copy(positionMs = it.currentPosition.toLong()) }
                delay(500)
            }
        }
    }

    private fun release() {
        ticker?.cancel()
        player?.release()
        player = null
    }
}
