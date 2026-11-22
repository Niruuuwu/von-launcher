package app.olauncher.media

import android.graphics.Bitmap

enum class NowPlayingState {
    ACTIVE,
    PAUSED,
    INACTIVE
}

data class NowPlaying(
    val title: String,
    val artist: String,
    val artKey: String,
    val art: Bitmap?,
    val state: NowPlayingState,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val positionUpdateTime: Long = 0L
) {
    val isPlaying: Boolean get() = state == NowPlayingState.ACTIVE
}
