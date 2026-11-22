package app.olauncher.media

import android.content.ComponentName
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn?.packageName == "com.spotify.music") {
            try {
                val largeIcon = sbn.notification?.getLargeIcon()
                if (largeIcon != null) {
                    val drawable = largeIcon.loadDrawable(this)
                    if (drawable is BitmapDrawable && drawable.bitmap != null) {
                        NowPlayingRepository.onNotificationArt(drawable.bitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.packageName == "com.spotify.music") {
            NowPlayingRepository.onNotificationArt(null)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, MediaListenerService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
