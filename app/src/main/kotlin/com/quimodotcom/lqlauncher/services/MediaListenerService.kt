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
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        checkActiveSessions()
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val token = extras.get(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token

        if (token != null) {
            updateMediaInfo(token)
        }
    }

    private fun checkActiveSessions() {
         try {
             val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
             val componentName = ComponentName(this, MediaListenerService::class.java)
             val sessions = mediaSessionManager?.getActiveSessions(componentName)

             if (sessions != null && sessions.isNotEmpty()) {
                 updateMediaInfo(sessions[0].sessionToken)
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

    private fun updateMediaInfo(token: MediaSession.Token) {
        val controller = MediaController(this, token)
        val metadata = controller.metadata ?: return

        // Try to get album art
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)

        // Check if song changed
        if (title == lastTitle && artist == lastArtist) {
            // Update bitmap if changed, but preserve current animated URL
            val currentState = MediaStateRepository.mediaState.value
            if (currentState != null && currentState.art != bitmap) {
                MediaStateRepository.update(currentState.copy(art = bitmap))
            }
            return
        }

        lastTitle = title
        lastArtist = artist

        // 1. Cancel previous fetch to prevent race conditions
        currentFetchJob?.cancel()

        // 2. Immediate update for responsiveness (show static art first)
        MediaStateRepository.update(MediaState(title, artist, bitmap, null, album))

        // 3. Fetch animated cover asynchronously
        if (title.isNotBlank() && artist.isNotBlank()) {
            currentFetchJob = serviceScope.launch {
                val animatedUrl = AppleMusicIntegration.searchAndGetAnimatedCover(title, artist, album)

                // Only update if we found a URL and the job is still active
                if (animatedUrl != null && isActive) {
                    MediaStateRepository.update(MediaState(title, artist, bitmap, animatedUrl, album))
                }
            }
        }
    }
}
