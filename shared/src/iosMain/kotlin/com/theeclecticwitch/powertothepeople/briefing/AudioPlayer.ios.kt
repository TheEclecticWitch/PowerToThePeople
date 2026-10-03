package com.theeclecticwitch.powertothepeople.briefing

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
actual object AudioPlayer {
    actual val playsInApp = true
    private val _state = MutableStateFlow(PlayerState())
    actual val state: StateFlow<PlayerState> = _state
    private var player: AVPlayer? = null
    private var ticker: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    actual fun play(url: String) {
        // Plays even with the ringer switch set to silent, as spoken audio apps do.
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)
        }
        if (player == null || _state.value.url != url) {
            val u = NSURL.URLWithString(url) ?: return
            player = AVPlayer(uRL = u)
            _state.value = PlayerState(url = url)
        }
        player?.play()
        _state.value = _state.value.copy(playing = true)
        tick()
    }

    actual fun pause() {
        player?.pause()
        _state.value = _state.value.copy(playing = false)
        ticker?.cancel()
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                val position = CMTimeGetSeconds(p.currentTime())
                val length = p.currentItem?.duration?.let { d -> CMTimeGetSeconds(d) } ?: 0.0
                val done = length > 0 && position >= length - 0.25
                _state.value = _state.value.copy(
                    positionMs = if (done) 0 else (position * 1000).toLong(),
                    durationMs = if (length.isFinite()) (length * 1000).toLong() else 0,
                    playing = !done && _state.value.playing,
                )
                if (done) {
                    p.seekToTime(CMTimeMake(0, 1))
                    break
                }
                delay(500)
            }
        }
    }
}
