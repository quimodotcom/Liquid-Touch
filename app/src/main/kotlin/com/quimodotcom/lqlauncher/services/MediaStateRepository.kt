package com.quimodotcom.lqlauncher.services

import android.graphics.Bitmap
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaState(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val animatedArtUrl: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false
)

object MediaStateRepository {
    private val _mediaState = MutableStateFlow<MediaState?>(null)
    val mediaState: StateFlow<MediaState?> = _mediaState.asStateFlow()

    private var activeController: MediaController? = null

    fun update(state: MediaState?, controller: MediaController? = null) {
        _mediaState.value = state
        if (controller != null) {
            activeController = controller
        }
    }

    fun playPause() {
        activeController?.transportControls?.let {
            val state = activeController?.playbackState?.state
            if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }
}
