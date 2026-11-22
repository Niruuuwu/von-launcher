package app.olauncher.timer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.R
import app.olauncher.databinding.ActivityTimeUpBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimeUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimeUpBinding
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var frictionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimeUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val targetPackage = intent.getStringExtra(TimerReminderService.EXTRA_PACKAGE) ?: ""
        val targetAppName = intent.getStringExtra(TimerReminderService.EXTRA_APP_NAME) ?: "App"
        val limitMinutes = intent.getIntExtra(TimerReminderService.EXTRA_LIMIT_MINUTES, 10)

        binding.tvTimeUpMessage.text = getString(R.string.time_limit_message, limitMinutes, targetAppName)

        binding.btnLeaveApp.setOnClickListener {
            leaveApp()
        }

        // Start mindful 5-second friction delay before extension buttons enable
        startFrictionCountdown()
    }

    private fun startFrictionCountdown() {
        binding.btnExtend1m.isEnabled = false
        binding.btnExtend5m.isEnabled = false
        binding.layoutExtendButtons.alpha = 0.35f

        var count = 5
        frictionJob = scope.launch {
            while (isActive && count > 0) {
                binding.tvExtendFriction.text = "Pause and reflect for ${count}s..."
                delay(1000)
                count--
            }

            if (isActive) {
                binding.tvExtendFriction.text = "Consciously extend time:"
                binding.layoutExtendButtons.alpha = 1.0f
                binding.btnExtend1m.isEnabled = true
                binding.btnExtend5m.isEnabled = true

                binding.btnExtend1m.setOnClickListener {
                    TimerReminderService.extendTimer(this@TimeUpActivity, 1)
                    finish()
                }

                binding.btnExtend5m.setOnClickListener {
                    TimerReminderService.extendTimer(this@TimeUpActivity, 5)
                    finish()
                }
            }
        }
    }

    private fun leaveApp() {
        TimerReminderService.stopTimer(this)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(homeIntent)
        finish()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Pressing back on time-up leaves the app to home
        leaveApp()
    }

    override fun onDestroy() {
        frictionJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
