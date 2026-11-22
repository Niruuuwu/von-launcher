package app.olauncher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object NowPlayingRepository {

    private const val TAG = "NowPlaying"
    private const val SPOTIFY_PACKAGE = "com.spotify.music"
    private const val MAX_ART_DIMENSION = 256

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var debounceJob: Job? = null

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    var lastActiveAt: Long = System.currentTimeMillis()
        private set

    private var appContext: Context? = null
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var sessionsListenerRegistered = false

    private var lastArtKey: String? = null
    private var cachedArtBitmap: Bitmap? = null
    private var lastNotificationArt: Bitmap? = null

    fun onNotificationArt(bitmap: Bitmap?) {
        lastNotificationArt = bitmap
        if (bitmap != null) {
            val current = _nowPlaying.value
            if (current != null && current.art == null) {
                processActiveSession()
            }
        }
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            try {
                processActiveSession()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onMetadataChanged", e)
                markInactive()
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            try {
                processActiveSession()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onPlaybackStateChanged", e)
                markInactive()
            }
        }

        override fun onSessionDestroyed() {
            try {
                Log.d(TAG, "Spotify session destroyed")
                detachController()
                markInactive()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onSessionDestroyed", e)
            }
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        try {
            findAndBindSpotifySession(controllers)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onActiveSessionsChanged", e)
            markInactive()
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        refresh()
    }

    fun isNotificationAccessGranted(context: Context? = appContext): Boolean {
        val ctx = context ?: appContext ?: return false
        return try {
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        } catch (e: Exception) {
            val enabledListeners = Settings.Secure.getString(
                ctx.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            enabledListeners.contains(ctx.packageName)
        }
    }

    fun refresh(context: Context? = null) {
        val ctx = (context ?: appContext)?.applicationContext ?: return
        appContext = ctx

        if (!isNotificationAccessGranted(ctx)) {
            Log.d(TAG, "Notification listener access not granted")
            markInactive(clearCompletely = true)
            return
        }

        try {
            val component = ComponentName(ctx, MediaListenerService::class.java)
            if (sessionManager == null) {
                sessionManager = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            }

            if (!sessionsListenerRegistered && sessionManager != null) {
                try {
                    sessionManager?.addOnActiveSessionsChangedListener(
                        sessionsChangedListener,
                        component,
                        Handler(Looper.getMainLooper())
                    )
                    sessionsListenerRegistered = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register active sessions listener", e)
                }
            }

            val controllers = sessionManager?.getActiveSessions(component)
            findAndBindSpotifySession(controllers)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading active sessions - listener access missing")
            markInactive(clearCompletely = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing sessions", e)
            markInactive()
        }
    }

    private fun findAndBindSpotifySession(controllers: List<MediaController>?) {
        val spotifyController = controllers?.firstOrNull { it.packageName == SPOTIFY_PACKAGE }

        if (spotifyController == null) {
            Log.d(TAG, "No active Spotify media session found")
            detachController()
            markInactive()
            return
        }

        if (activeController?.sessionToken != spotifyController.sessionToken) {
            detachController()
            activeController = spotifyController
            activeController?.registerCallback(mediaControllerCallback, Handler(Looper.getMainLooper()))
            Log.d(TAG, "Bound to Spotify media session")
        }

        processActiveSession()
    }

    private fun detachController() {
        try {
            activeController?.unregisterCallback(mediaControllerCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activeController = null
        }
    }

    private fun processActiveSession() {
        val controller = activeController
        if (controller == null) {
            markInactive()
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val nowPlayingState = when (playbackState?.state) {
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> NowPlayingState.ACTIVE

            PlaybackState.STATE_PAUSED -> NowPlayingState.PAUSED

            else -> NowPlayingState.INACTIVE
        }

        if (nowPlayingState == NowPlayingState.ACTIVE) {
            lastActiveAt = System.currentTimeMillis()
        }

        if (metadata == null) {
            if (nowPlayingState == NowPlayingState.INACTIVE) {
                markInactive()
            }
            return
        }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""

        // Ad detection per PRD 3.5: keep previous track theme/info
        if (title.equals("Advertisement", ignoreCase = true) || (title.isNotEmpty() && artist.isEmpty())) {
            Log.d(TAG, "Ad detected ('$title'), keeping current track state")
            return
        }

        if (title.isEmpty() && artist.isEmpty()) {
            if (nowPlayingState == NowPlayingState.INACTIVE) {
                markInactive()
            }
            return
        }

        val artKey = "$title|$artist|$album"

        // Debounce rapid skipping by 150ms when track changes
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (artKey != lastArtKey || cachedArtBitmap == null) {
                delay(150)
            }

            val artBitmap: Bitmap?
            if (artKey == lastArtKey && cachedArtBitmap != null) {
                artBitmap = cachedArtBitmap
            } else {
                artBitmap = resolveAlbumArt(controller, metadata)
                lastArtKey = artKey
                cachedArtBitmap = artBitmap
            }

            Log.d(
                TAG,
                "NowPlaying update: title='$title', artist='$artist', artKey='$artKey', " +
                        "artSize=${artBitmap?.width ?: 0}x${artBitmap?.height ?: 0}, state=$nowPlayingState"
            )

            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val playbackState = controller.playbackState
            val position = playbackState?.position ?: 0L
            val playbackSpeed = playbackState?.playbackSpeed ?: 1f
            val lastUpdateTime = playbackState?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()

            _nowPlaying.value = NowPlaying(
                title = title,
                artist = artist,
                artKey = artKey,
                art = artBitmap,
                state = nowPlayingState,
                durationMs = duration,
                positionMs = position,
                playbackSpeed = playbackSpeed,
                positionUpdateTime = lastUpdateTime
            )
        }
    }

    private suspend fun resolveAlbumArt(controller: MediaController, metadata: MediaMetadata): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                // 1. METADATA_KEY_ALBUM_ART
                var rawBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

                // 2. METADATA_KEY_ART
                if (rawBitmap == null) {
                    rawBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                }

                // 3. Real album art URIs (prioritize before generic display icons)
                if (rawBitmap == null) {
                    val uriString = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                        ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                    if (!uriString.isNullOrBlank()) {
                        rawBitmap = loadArtUri(uriString)
                    }
                }

                // 4. Notification largeIcon fallback
                if (rawBitmap == null && lastNotificationArt != null) {
                    rawBitmap = lastNotificationArt
                }

                // 5. METADATA_KEY_DISPLAY_ICON (last resort: may be generic placeholder icon)
                if (rawBitmap == null) {
                    rawBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                }

                // 6. METADATA_KEY_DISPLAY_ICON_URI
                if (rawBitmap == null) {
                    val uriString = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                    if (!uriString.isNullOrBlank()) {
                        rawBitmap = loadArtUri(uriString)
                    }
                }

                rawBitmap?.let { downscale(it, MAX_ART_DIMENSION) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed resolving album art", e)
                null
            }
        }

    private fun loadArtUri(uriString: String): Bitmap? {
        return try {
            if (uriString.startsWith("http://", ignoreCase = true) || uriString.startsWith("https://", ignoreCase = true)) {
                val url = java.net.URL(uriString)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                val uri = Uri.parse(uriString)
                val ctx = appContext ?: return null
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load art from URI: $uriString", e)
            null
        }
    }

    private fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (ratio > 1f) {
            targetWidth = maxDim
            targetHeight = (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDim
            targetWidth = (maxDim * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun markInactive(clearCompletely: Boolean = false) {
        debounceJob?.cancel()
        val current = _nowPlaying.value
        if (clearCompletely || current == null) {
            lastArtKey = null
            cachedArtBitmap = null
            _nowPlaying.value = null
        } else {
            _nowPlaying.value = current.copy(state = NowPlayingState.INACTIVE)
        }
    }

    fun skipToNext() {
        try {
            activeController?.transportControls?.skipToNext()
        } catch (e: Exception) {
            Log.e(TAG, "Error in skipToNext", e)
        }
    }

    fun skipToPrevious() {
        try {
            activeController?.transportControls?.skipToPrevious()
        } catch (e: Exception) {
            Log.e(TAG, "Error in skipToPrevious", e)
        }
    }

    fun togglePlayPause() {
        try {
            val state = activeController?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                activeController?.transportControls?.pause()
            } else {
                activeController?.transportControls?.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in togglePlayPause", e)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            activeController?.transportControls?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error in seekTo", e)
        }
    }
}
