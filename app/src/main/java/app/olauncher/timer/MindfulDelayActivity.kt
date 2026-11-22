package app.olauncher.timer

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.databinding.ActivityMindfulDelayBinding
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MindfulDelayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "EXTRA_PACKAGE"
        const val EXTRA_APP_NAME = "EXTRA_APP_NAME"
        const val EXTRA_CLASS_NAME = "EXTRA_CLASS_NAME"
        const val EXTRA_USER_STRING = "EXTRA_USER_STRING"
        const val EXTRA_DELAY_SECONDS = "EXTRA_DELAY_SECONDS"
        const val EXTRA_LIMIT_MINUTES = "EXTRA_LIMIT_MINUTES"
        const val EXTRA_SHOW_BADGE = "EXTRA_SHOW_BADGE"
    }

    private lateinit var binding: ActivityMindfulDelayBinding
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var countdownJob: Job? = null
    private var pulseAnimator: ObjectAnimator? = null

    private var targetPackage: String = ""
    private var targetAppName: String = ""
    private var targetClassName: String? = null
    private var userString: String = ""
    private var delaySeconds: Int = 10
    private var limitMinutes: Int = 0
    private var showFloatingBadge: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMindfulDelayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        targetAppName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
        targetClassName = intent.getStringExtra(EXTRA_CLASS_NAME)
        userString = intent.getStringExtra(EXTRA_USER_STRING) ?: ""
        delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 10)
        limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 0)
        showFloatingBadge = intent.getBooleanExtra(EXTRA_SHOW_BADGE, true)

        binding.tvMindfulAppName.text = targetAppName
        binding.tvCountdownNumber.text = delaySeconds.toString()
        binding.tvOpeningIn.text = getString(R.string.opening_in_seconds, delaySeconds)

        binding.btnCancel.setOnClickListener {
            // Cancel opening, return cleanly to launcher
            finish()
        }

        binding.btnOpenNow.setOnClickListener {
            launchTargetAppAndFinish()
        }

        startBreathingAnimation()
        startCountdown()
    }

    private fun startBreathingAnimation() {
        val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.18f)
        val scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.18f)
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.circleContainer, scaleX, scaleY).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun startCountdown() {
        var remaining = delaySeconds
        countdownJob = scope.launch {
            while (isActive && remaining > 0) {
                binding.tvCountdownNumber.text = remaining.toString()
                binding.tvOpeningIn.text = getString(R.string.opening_in_seconds, remaining)
                delay(1000)
                remaining--
            }
            if (isActive) {
                launchTargetAppAndFinish()
            }
        }
    }

    private fun launchTargetAppAndFinish() {
        countdownJob?.cancel()

        try {
            val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val userHandle = if (userString.isNotEmpty()) {
                getUserHandleFromString(this, userString)
            } else {
                Process.myUserHandle()
            }

            if (!targetClassName.isNullOrEmpty()) {
                val component = ComponentName(targetPackage, targetClassName!!)
                launcher.startMainActivity(component, userHandle, null, null)
            } else {
                val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    showToast(getString(R.string.unable_to_launch_app))
                }
            }

            // Start timer service if session limit configured
            if (limitMinutes > 0) {
                TimerReminderService.startTimer(
                    this,
                    targetPackage,
                    targetAppName,
                    limitMinutes,
                    showFloatingBadge
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(getString(R.string.unable_to_launch_app))
        }

        finish()
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        scope.cancel()
        pulseAnimator?.cancel()
        super.onDestroy()
    }
}
