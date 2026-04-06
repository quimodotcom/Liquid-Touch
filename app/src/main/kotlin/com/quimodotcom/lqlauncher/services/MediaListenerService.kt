package com.quimodotcom.lqlauncher.services

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import com.quimodotcom.lqlauncher.helpers.AppleMusicIntegration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private val _activeNotificationPackages = MutableStateFlow<Set<String>>(emptySet())
        val activeNotificationPackages = _activeNotificationPackages.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        checkActiveSessions()
        updateActiveNotifications()

        val filter = IntentFilter("com.quimodotcom.lqlauncher.CANCEL_NOTIFICATION")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(cancelReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(cancelReceiver)
        } catch (e: Exception) {}
    }

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val key = intent?.getStringExtra("key")
            if (key != null) {
                cancelNotification(key)
                updateActiveNotifications()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn)
        updateActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        checkActiveSessions()
        updateActiveNotifications()
    }

    private fun updateActiveNotifications() {
        try {
            val active = activeNotifications
            val packages = active.map { it.packageName }.toSet()
            _activeNotificationPackages.value = packages

            // Build List for Repository (Filtering Media/Self)
            val items = active.mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

                // Ignore media notifications (Now Playing) and self
                val isMedia = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                               sbn.notification.category == Notification.CATEGORY_TRANSPORT
                val isSelf = sbn.packageName == packageName

                if (title.isNotBlank() && !isMedia && !isSelf) {
                    NotificationItem(
                        id = sbn.id,
                        key = sbn.key,
                        packageName = sbn.packageName,
                        title = title,
                        text = text,
                        icon = sbn.notification.smallIcon,
                        timestamp = sbn.postTime
                    )
                } else null
            }.sortedByDescending { it.timestamp }

            MediaStateRepository.updateNotifications(items)

        } catch (e: Exception) {
            Log.e("MediaListenerService", "Error updating active notifications", e)
        }
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
    private var activeController: MediaController? = null

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            val playing = state?.state == android.media.session.PlaybackState.STATE_PLAYING
            val current = MediaStateRepository.mediaState.value
            if (current != null && current.isPlaying != playing) {
                MediaStateRepository.update(current.copy(isPlaying = playing))
            }
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            checkActiveSessions()
        }
    }

    private fun updateMediaInfo(token: MediaSession.Token) {
        val controller = MediaController(this, token)
        val metadata = controller.metadata ?: return
        val playbackState = controller.playbackState

        // Handle Controller Callbacks to avoid leaks and recursion
        if (activeController?.sessionToken != token) {
            activeController?.unregisterCallback(mediaCallback)
            activeController = controller
            activeController?.registerCallback(mediaCallback)
        }

        // Try to get album art
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val isPlaying = playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING

        // Check if song changed
        if (title == lastTitle && artist == lastArtist) {
            // Update state if changed, but preserve current animated URL
            val currentState = MediaStateRepository.mediaState.value
            if (currentState != null && (currentState.art != bitmap || currentState.isPlaying != isPlaying)) {
                MediaStateRepository.update(currentState.copy(art = bitmap, isPlaying = isPlaying), controller)
            }
            return
        }

        lastTitle = title
        lastArtist = artist

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
                    val currentState = MediaStateRepository.mediaState.value
                    MediaStateRepository.update(currentState?.copy(animatedArtUrl = animatedUrl) ?: MediaState(title, artist, bitmap, animatedUrl, album, isPlaying), controller)
                }
            }
        }

    }
}
