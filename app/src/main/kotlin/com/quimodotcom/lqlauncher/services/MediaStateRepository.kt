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
    val album: String? = null
)

object MediaStateRepository {
    private val _mediaState = MutableStateFlow<MediaState?>(null)
    val mediaState: StateFlow<MediaState?> = _mediaState.asStateFlow()

    fun update(state: MediaState?) {
        _mediaState.value = state
    }
}
