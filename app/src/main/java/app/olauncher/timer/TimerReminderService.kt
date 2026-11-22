package app.olauncher.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.olauncher.MainActivity
import app.olauncher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerReminderService : Service() {

    companion object {
        const val ACTION_START = "app.olauncher.timer.START"
        const val ACTION_STOP = "app.olauncher.timer.STOP"
        const val ACTION_EXTEND = "app.olauncher.timer.EXTEND"
        const val ACTION_LAUNCHER_RESUMED = "app.olauncher.timer.LAUNCHER_RESUMED"

        const val EXTRA_PACKAGE = "EXTRA_PACKAGE"
        const val EXTRA_APP_NAME = "EXTRA_APP_NAME"
        const val EXTRA_LIMIT_MINUTES = "EXTRA_LIMIT_MINUTES"
        const val EXTRA_SHOW_BADGE = "EXTRA_SHOW_BADGE"
        const val EXTRA_EXTEND_MINUTES = "EXTRA_EXTEND_MINUTES"

        private const val NOTIFICATION_ID = 9182
        private const val CHANNEL_ID = "olauncher_timer_channel"

        var isServiceRunning = false
            private set
        var activePackageName: String? = null
            private set

        fun startTimer(
            context: Context,
            packageName: String,
            appName: String,
            limitMinutes: Int,
            showBadge: Boolean = true
        ) {
            val intent = Intent(context, TimerReminderService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_LIMIT_MINUTES, limitMinutes)
                putExtra(EXTRA_SHOW_BADGE, showBadge)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTimer(context: Context) {
            val intent = Intent(context, TimerReminderService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun extendTimer(context: Context, extraMinutes: Int) {
            val intent = Intent(context, TimerReminderService::class.java).apply {
                action = ACTION_EXTEND
                putExtra(EXTRA_EXTEND_MINUTES, extraMinutes)
            }
            context.startService(intent)
        }

        fun notifyLauncherResumed(context: Context) {
            if (isServiceRunning) {
                val intent = Intent(context, TimerReminderService::class.java).apply {
                    action = ACTION_LAUNCHER_RESUMED
                }
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    private var targetPackage: String = ""
    private var targetAppName: String = ""
    private var remainingSeconds: Int = 0
    private var initialLimitMinutes: Int = 0
    private var showFloatingBadge: Boolean = true

    private var floatingBadge: FloatingTimerView? = null
    private var consecutiveSecondsOutOfApp: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
                targetAppName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
                initialLimitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 10)
                showFloatingBadge = intent.getBooleanExtra(EXTRA_SHOW_BADGE, true)
                remainingSeconds = initialLimitMinutes * 60
                activePackageName = targetPackage
                consecutiveSecondsOutOfApp = 0

                startForeground(NOTIFICATION_ID, buildNotification(formatRemainingTime(remainingSeconds)))
                setupFloatingBadge()
                startTicker()
            }

            ACTION_EXTEND -> {
                val extraMinutes = intent.getIntExtra(EXTRA_EXTEND_MINUTES, 5)
                remainingSeconds += extraMinutes * 60
                consecutiveSecondsOutOfApp = 0
                setupFloatingBadge()
                startTicker()
            }

            ACTION_LAUNCHER_RESUMED -> {
                // If the user returned to launcher and remains there, auto stop after a brief moment
                consecutiveSecondsOutOfApp += 3
                if (consecutiveSecondsOutOfApp >= 10) {
                    stopSelf()
                }
            }

            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun setupFloatingBadge() {
        if (!showFloatingBadge) return
        if (floatingBadge == null) {
            floatingBadge = FloatingTimerView(this) {
                // User tapped the floating pill: open quick time up / reflection screen
                showTimeUpScreen()
            }
        }
        floatingBadge?.show()
        floatingBadge?.updateTime(formatRemainingTime(remainingSeconds))
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--

                val formatted = formatRemainingTime(remainingSeconds)
                floatingBadge?.updateTime(formatted)

                // Update notification periodically
                if (remainingSeconds % 10 == 0) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(formatted))
                }

                // Check top foreground app every 3 seconds to detect if user left the timed app
                if (remainingSeconds % 3 == 0) {
                    checkForegroundApp()
                }
            }

            if (remainingSeconds <= 0) {
                onTimerFinished()
            }
        }
    }

    private fun checkForegroundApp() {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 10000
            val events = usageStatsManager.queryEvents(startTime, endTime) ?: return

            var lastPackage: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastPackage = event.packageName
                }
            }

            if (lastPackage != null && lastPackage != targetPackage && lastPackage != packageName) {
                consecutiveSecondsOutOfApp += 3
                if (consecutiveSecondsOutOfApp >= 30) {
                    // User has spent 30s in another app; stop the timer
                    stopSelf()
                }
            } else if (lastPackage == targetPackage) {
                consecutiveSecondsOutOfApp = 0
            }
        } catch (_: Exception) {
            // Usage access may not be granted; ticker continues normally
        }
    }

    private fun onTimerFinished() {
        floatingBadge?.hide()
        showTimeUpScreen()
    }

    private fun showTimeUpScreen() {
        val intent = Intent(this, TimeUpActivity::class.java).apply {
            putExtra(EXTRA_PACKAGE, targetPackage)
            putExtra(EXTRA_APP_NAME, targetAppName)
            putExtra(EXTRA_LIMIT_MINUTES, initialLimitMinutes)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun formatRemainingTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun buildNotification(timeStr: String): Notification {
        val stopIntent = Intent(this, TimerReminderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val homeIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val homePendingIntent = PendingIntent.getActivity(
            this,
            2,
            homeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(targetAppName)
            .setContentText("$timeStr remaining")
            .setSmallIcon(R.drawable.ic_timer)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(homePendingIntent)
            .addAction(R.drawable.ic_close, getString(R.string.leave_app), stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Olauncher App Timers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows remaining usage time for active timed apps"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        activePackageName = null
        timerJob?.cancel()
        serviceScope.cancel()
        floatingBadge?.hide()
        floatingBadge = null
        super.onDestroy()
    }
}
