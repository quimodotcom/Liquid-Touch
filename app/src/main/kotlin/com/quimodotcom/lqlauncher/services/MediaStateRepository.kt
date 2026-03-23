package com.quimodotcom.lqlauncher.services

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaState(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val animatedArtUrl: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false,
    val canSkipNext: Boolean = true,
    val canSkipPrev: Boolean = true
)

enum class MediaCommand {
    PLAY, PAUSE, TOGGLE, NEXT, PREVIOUS
}

object MediaStateRepository {
    private val _mediaState = MutableStateFlow<MediaState?>(null)
    val mediaState: StateFlow<MediaState?> = _mediaState.asStateFlow()

    private var commandHandler: ((MediaCommand) -> Unit)? = null

    fun update(state: MediaState?) {
        _mediaState.value = state
    }

    fun setCommandHandler(handler: ((MediaCommand) -> Unit)?) {
        commandHandler = handler
    }

    fun sendCommand(command: MediaCommand) {
        commandHandler?.invoke(command)
    }
}
