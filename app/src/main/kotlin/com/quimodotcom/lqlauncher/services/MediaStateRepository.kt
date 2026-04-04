package com.quimodotcom.lqlauncher.services

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import android.media.session.MediaController
import android.media.session.PlaybackState

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

    private var currentController: MediaController? = null

    fun update(state: MediaState?, controller: MediaController? = null) {
        _mediaState.value = state
        if (controller != null) {
            currentController = controller
        }
    }

    fun togglePlayPause() {
        val controller = currentController ?: return
        val pbState = controller.playbackState?.state
        if (pbState == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipNext() {
        currentController?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        currentController?.transportControls?.skipToPrevious()
    }
}
