package com.quimodotcom.lqlauncher.services

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.quimodotcom.lqlauncher.helpers.AppleMusicIntegration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onListenerConnected() {
        super.onListenerConnected()
        checkActiveSessions()
        refreshNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn)
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        checkActiveSessions()
        refreshNotifications()
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val token = extras.get(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token

        if (token != null) {
            updateMediaInfo(token)
        }
    }

    private fun refreshNotifications() {
        try {
            val activeNotifications = activeNotifications
            val notificationItems = activeNotifications.filter { sbn ->
                // Filter out non-clearable (ongoing) notifications and those without title/text
                val isClearable = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) == 0 &&
                                  (sbn.notification.flags and Notification.FLAG_NO_CLEAR) == 0
                val hasTitle = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE) != null

                isClearable && hasTitle
            }.map { sbn ->
                NotificationItem(
                    key = sbn.key,
                    title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                    text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
                    packageName = sbn.packageName,
                    postTime = sbn.postTime
                )
            }.sortedByDescending { it.postTime }

            MediaStateRepository.updateNotifications(notificationItems)
        } catch (e: Exception) {
            Log.e("MediaListenerService", "Error refreshing notifications", e)
        }
    }

    private fun checkActiveSessions() {
         try {
             val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
             val componentName = ComponentName(this, MediaListenerService::class.java)
             val sessions = mediaSessionManager?.getActiveSessions(componentName)

             if (sessions != null && sessions.isNotEmpty()) {
                 updateMediaInfo(sessions[0])
             } else {
                 MediaStateRepository.update(null)
             }
         } catch (e: Exception) {
             Log.e("MediaListenerService", "Error checking active sessions", e)
         }
    }

    private var currentFetchJob: Job? = null
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private var lastIsPlaying: Boolean = false

    private fun updateMediaInfo(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val playbackState = controller.playbackState
        val isPlaying = playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING

        // Try to get album art
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)

        // Check if song or playback state changed
        if (title == lastTitle && artist == lastArtist && isPlaying == lastIsPlaying) {
            // Update bitmap if changed, but preserve current animated URL
            val currentState = MediaStateRepository.mediaState.value
            if (currentState != null && currentState.art != bitmap) {
                MediaStateRepository.update(currentState.copy(art = bitmap), controller)
            }
            return
        }

        lastTitle = title
        lastArtist = artist
        lastIsPlaying = isPlaying

        // 1. Cancel previous fetch to prevent race conditions
        currentFetchJob?.cancel()

        // 2. Immediate update for responsiveness (show static art first)
        MediaStateRepository.update(MediaState(title, artist, bitmap, null, album, isPlaying), controller)

        // 3. Fetch animated cover asynchronously
        if (title.isNotBlank() && artist.isNotBlank()) {
            currentFetchJob = serviceScope.launch {
                val animatedUrl = AppleMusicIntegration.searchAndGetAnimatedCover(title, artist, album)

                // Only update if we found a URL and the job is still active
                if (animatedUrl != null && isActive) {
                    MediaStateRepository.update(MediaState(title, artist, bitmap, animatedUrl, album, isPlaying), controller)
                }
            }
        }
    }

    private fun updateMediaInfo(token: MediaSession.Token) {
        val controller = MediaController(this, token)
        updateMediaInfo(controller)
    }
}
