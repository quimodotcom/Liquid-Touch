package com.quimodotcom.lqlauncher.services

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
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

data class NotificationItem(
    val id: Int,
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val icon: Icon?,
    val timestamp: Long,
    val contentIntent: PendingIntent? = null
)

object MediaStateRepository {
    private val _mediaState = MutableStateFlow<MediaState?>(null)
    val mediaState: StateFlow<MediaState?> = _mediaState.asStateFlow()

    private val _activeNotifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val activeNotifications: StateFlow<List<NotificationItem>> = _activeNotifications.asStateFlow()

    private var activeController: MediaController? = null

    fun update(state: MediaState?, controller: MediaController? = null) {
        _mediaState.value = state
        if (controller != null) {
            activeController = controller
        }
    }

    fun updateNotifications(notifications: List<NotificationItem>) {
        _activeNotifications.value = notifications
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
