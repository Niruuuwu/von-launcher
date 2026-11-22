package app.olauncher.media

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.olauncher.R
import app.olauncher.databinding.ViewNowPlayingBinding

class NowPlayingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewNowPlayingBinding.inflate(LayoutInflater.from(context), this, true)

    private var currentArtKey: String? = null
    private var currentTitle: String? = null
    private var currentArtist: String? = null

    private var hasCornerShortcuts: Boolean = true
    private var navInsetBottom: Int = 0

    init {
        isClickable = false
        isFocusable = false

        // 16dp rounded corners for 72dp cover
        val cornerRadius = 16f.dpToPx()
        binding.coverContainer.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        binding.coverContainer.clipToOutline = true

        // Playback control click handlers
        binding.btnPrevious.setOnClickListener {
            NowPlayingRepository.skipToPrevious()
        }

        binding.btnPlayPause.setOnClickListener {
            NowPlayingRepository.togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            NowPlayingRepository.skipToNext()
        }

        // Interactive wavy progress scrubbing
        binding.wavyProgressBar.onSeekListener = { seekMs ->
            NowPlayingRepository.seekTo(seekMs)
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            navInsetBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            updateLayoutMargins()
            insets
        }
    }

    fun setCornerShortcutsEnabled(enabled: Boolean) {
        hasCornerShortcuts = enabled
        updateLayoutMargins()
    }

    private fun updateLayoutMargins() {
        val baseBottom = if (hasCornerShortcuts) 96f.dpToPx().toInt() else 40f.dpToPx().toInt()
        val totalBottomMargin = baseBottom + navInsetBottom
        val horizontalMargin = 24f.dpToPx().toInt()

        val params = (layoutParams as? MarginLayoutParams) ?: return
        if (params.bottomMargin != totalBottomMargin ||
            params.leftMargin != horizontalMargin ||
            params.rightMargin != horizontalMargin
        ) {
            params.leftMargin = horizontalMargin
            params.rightMargin = horizontalMargin
            params.bottomMargin = totalBottomMargin
            layoutParams = params
        }
    }

    fun setNowPlaying(nowPlaying: NowPlaying?) {
        if (nowPlaying == null || nowPlaying.state == NowPlayingState.INACTIVE) {
            fadeOutAndHide(ThemeBackground.TRANSITION_THEME_TO_IDLE)
            return
        }

        updateLayoutMargins()

        // Update play/pause button icon
        val isPlaying = nowPlaying.isPlaying
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )

        // Update wavy progress bar
        binding.wavyProgressBar.updateProgress(
            position = nowPlaying.positionMs,
            duration = nowPlaying.durationMs,
            speed = nowPlaying.playbackSpeed,
            updateTime = nowPlaying.positionUpdateTime,
            playing = isPlaying
        )

        val titleChanged = currentTitle != nowPlaying.title
        val artistChanged = currentArtist != nowPlaying.artist
        val artChanged = currentArtKey != nowPlaying.artKey

        currentTitle = nowPlaying.title
        currentArtist = nowPlaying.artist
        currentArtKey = nowPlaying.artKey

        if (visibility != View.VISIBLE) {
            visibility = View.VISIBLE
            alpha = 0f
            animate().alpha(1f).setDuration(ThemeBackground.TRANSITION_IDLE_TO_THEME).start()

            // Immediate setup for first show
            binding.tvSongTitle.text = nowPlaying.title
            binding.tvArtist.text = nowPlaying.artist

            if (nowPlaying.art != null) {
                binding.coverContainer.visibility = View.VISIBLE
                binding.ivCover.setImageBitmap(nowPlaying.art)
            } else {
                binding.coverContainer.visibility = View.GONE
            }
            return
        }

        // Active change: Cover crossfades over 300ms, text swaps with 150ms fade
        if (artChanged) {
            if (nowPlaying.art != null) {
                binding.coverContainer.visibility = View.VISIBLE
                binding.ivCoverIncoming.setImageBitmap(nowPlaying.art)
                binding.ivCoverIncoming.alpha = 0f
                binding.ivCoverIncoming.visibility = View.VISIBLE

                binding.ivCoverIncoming.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.ivCover.setImageBitmap(nowPlaying.art)
                        binding.ivCoverIncoming.visibility = View.GONE
                    }
                })
                .start()
            } else {
                binding.coverContainer.visibility = View.GONE
            }
        }

        if (titleChanged || artistChanged) {
            binding.textContainer.animate()
                .alpha(0f)
                .setDuration(75)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.tvSongTitle.text = nowPlaying.title
                        binding.tvArtist.text = nowPlaying.artist
                        binding.textContainer.animate()
                            .alpha(1f)
                            .setDuration(75)
                            .start()
                    }
                })
                .start()
        }
    }

    private fun fadeOutAndHide(durationMs: Long) {
        if (visibility != View.VISIBLE) return
        animate()
            .alpha(0f)
            .setDuration(durationMs)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                    binding.ivCover.setImageDrawable(null)
                    binding.tvSongTitle.text = ""
                    binding.tvArtist.text = ""
                    binding.wavyProgressBar.updateProgress(0L, 0L, 1f, 0L, false)
                    currentArtKey = null
                    currentTitle = null
                    currentArtist = null
                }
            })
            .start()
    }

    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)
}
