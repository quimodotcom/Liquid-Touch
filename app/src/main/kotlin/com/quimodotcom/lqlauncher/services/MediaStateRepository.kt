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

data class NotificationItem(
    val key: String,
    val title: String,
    val text: String,
    val packageName: String,
    val postTime: Long
)

object MediaStateRepository {
    private val _mediaState = MutableStateFlow<MediaState?>(null)
    val mediaState: StateFlow<MediaState?> = _mediaState.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

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

    fun updateNotifications(notifications: List<NotificationItem>) {
        _notifications.value = notifications
    }
}
