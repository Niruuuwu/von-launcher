package app.olauncher.data

import org.json.JSONObject

data class AppTimerConfig(
    val packageName: String,
    val appName: String,
    val delaySeconds: Int = 0,     // 0 = off, 5, 10, 15, 30
    val limitMinutes: Int = 0,     // 0 = off, 1, 5, 10, 15, 20, 30, 45, 60
    val showFloatingBadge: Boolean = true,
) {
    fun isConfigured(): Boolean = delaySeconds > 0 || limitMinutes > 0

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("packageName", packageName)
            put("appName", appName)
            put("delaySeconds", delaySeconds)
            put("limitMinutes", limitMinutes)
            put("showFloatingBadge", showFloatingBadge)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AppTimerConfig {
            return AppTimerConfig(
                packageName = json.optString("packageName", ""),
                appName = json.optString("appName", ""),
                delaySeconds = json.optInt("delaySeconds", 0),
                limitMinutes = json.optInt("limitMinutes", 0),
                showFloatingBadge = json.optBoolean("showFloatingBadge", true),
            )
        }
    }
}
