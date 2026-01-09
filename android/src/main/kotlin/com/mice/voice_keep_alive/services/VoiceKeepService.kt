package com.mice.voice_keep_alive.services

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.mice.voice_keep_alive.R
import com.mice.voice_keep_alive.audio.SilentAudioPlayer

class VoiceKeepService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_service_channel"
        const val NOTIFICATION_ID = 1001

        const val MODE_AUDIENCE = 0
        const val MODE_ANCHOR = 1

        @Volatile
        var instance: VoiceKeepService? = null
    }

    private var currentMode = MODE_AUDIENCE
    private var wakeLock: PowerManager.WakeLock? = null
    private val silentPlayer = SilentAudioPlayer()

    private var title = ""
    private var content = ""
    private var roomParams = ""

    // ---------------- lifecycle ----------------
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        releaseWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentMode = intent?.getIntExtra("mode", MODE_AUDIENCE) ?: MODE_AUDIENCE
        title = intent?.getStringExtra("title") ?: getString(R.string.voice_service_title)
        content = intent?.getStringExtra("content") ?: getString(R.string.voice_service_text)
        roomParams = intent?.getStringExtra("roomParams") ?: ""
        startForegroundSafely()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ================= 音频状态回调 =================
    fun onAudioStateChanged(active: Boolean) {
        if (!active ||
            currentMode != MODE_ANCHOR ||
            !isAppInBackground() ||
            !hasMicPermission()
        ) {
            silentPlayer.stop()
            releaseWakeLock()
            return
        }

        // 🎯 抖音核心：后台持续音频链路
        silentPlayer.start()

        // ⚠️ WakeLock 兜底
        acquireWakeLock(30_000L)
    }

    // ================= WakeLock =================
    private fun acquireWakeLock(timeoutMs: Long) {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoiceKeepService::WakeLock"
            )
        }

        if (wakeLock?.isHeld != true) {
            try { wakeLock?.acquire(timeoutMs) } catch (_: Exception) {}
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
    }

    // ================= 前台服务 =================
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startForegroundSafely() {
        val notification = buildNotification()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 仅在有权限且主播模式时传 MIC 类型
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                    if (currentMode == MODE_ANCHOR && hasMicPermission())
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    else 0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0

        startForeground(NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, Class.forName("$packageName.MainActivity"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra("roomParams", roomParams)

        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (currentMode == MODE_ANCHOR)
                    android.R.drawable.ic_btn_speak_now
                else
                    android.R.drawable.ic_lock_silent_mode_off
            )
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.voice_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    // ================= 权限检查 =================
    private fun hasMicPermission(): Boolean {
        val recordAudio = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val fgsMic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            ) == PackageManager.PERMISSION_GRANTED
        else true

        return recordAudio && fgsMic
    }

    private fun isAppInBackground(): Boolean {
        return !VoiceKeepApp.isForeground
    }

    // ================= 生命周期 =================
    override fun onDestroy() {
        silentPlayer.stop()
        releaseWakeLock()
        instance = null
        stopForeground(true)
        super.onDestroy()
    }
}


//package com.mice.voice_keep_alive.services
//
//import android.Manifest
//import android.app.*
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.content.pm.ServiceInfo
//import android.os.*
//import androidx.core.app.ActivityCompat
//import androidx.core.app.NotificationCompat
//import com.mice.voice_keep_alive.R
//import com.mice.voice_keep_alive.audio.SilentAudioPlayer
//
//class VoiceKeepService : Service() {
//
//    companion object {
//        const val CHANNEL_ID = "voice_service_channel"
//        const val NOTIFICATION_ID = 1001
//
//        const val MODE_AUDIENCE = 0
//        const val MODE_ANCHOR = 1
//
//        @Volatile
//        var instance: VoiceKeepService? = null
//    }
//
//    private var currentMode = MODE_AUDIENCE
//    private var wakeLock: PowerManager.WakeLock? = null
//    private val silentPlayer = SilentAudioPlayer()
//    private var title = ""
//    private var content = ""
//    private var roomParams = ""
//
//    override fun onCreate() {
//        super.onCreate()
//        instance = this
//        createNotificationChannel()
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        currentMode = intent?.getIntExtra("mode", MODE_AUDIENCE) ?: MODE_AUDIENCE
//        title = intent?.getStringExtra("title") ?: getString(R.string.voice_service_title)
//        content = intent?.getStringExtra("content") ?: getString(R.string.voice_service_text)
//        roomParams = intent?.getStringExtra("roomParams") ?: ""
//
//        startForegroundSafely()
//        return START_STICKY
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    // ================= 音频状态回调 =================
//
//    fun onAudioStateChanged(active: Boolean) {
//        if (!active ||
//            currentMode != MODE_ANCHOR ||
//            !isAppInBackground() ||
//            !hasMicPermission()
//        ) {
////            silentPlayer.stop()
//            releaseWakeLock()
//            return
//        }
//
//        // 🎯 抖音核心：后台持续音频链路
////        silentPlayer.start()
//
//        // ⚠️ WakeLock 只是兜底
//        acquireWakeLock(30_000L)
//    }
//
//    // ================= WakeLock =================
//
//    private fun acquireWakeLock(timeoutMs: Long) {
//        if (wakeLock == null) {
//            val pm = getSystemService(POWER_SERVICE) as PowerManager
//            wakeLock = pm.newWakeLock(
//                PowerManager.PARTIAL_WAKE_LOCK,
//                "VoiceKeepService::WakeLock"
//            )
//        }
//
//        if (wakeLock?.isHeld != true) {
//            try {
//                wakeLock?.acquire(timeoutMs)
//            } catch (_: Exception) {}
//        }
//    }
//
//    private fun releaseWakeLock() {
//        try {
//            if (wakeLock?.isHeld == true) {
//                wakeLock?.release()
//            }
//        } catch (_: Exception) {}
//    }
//
//    // ================= 前台服务 =================
//
//    private fun startForegroundSafely() {
//        val notification = buildNotification()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            startForeground(
//                NOTIFICATION_ID,
//                notification,
//                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
//            )
//        } else {
//            startForeground(NOTIFICATION_ID, notification)
//        }
//    }
//
//    private fun buildNotification(): Notification {
//        val intent = packageManager.getLaunchIntentForPackage(packageName)
//            ?: Intent(this, Class.forName("$packageName.MainActivity"))
//        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
//                Intent.FLAG_ACTIVITY_SINGLE_TOP or
//                Intent.FLAG_ACTIVITY_CLEAR_TOP
//        intent.putExtra("roomParams", roomParams)
//
//        val pi = PendingIntent.getActivity(
//            this,
//            0,
//            intent,
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//        )
//
//        return NotificationCompat.Builder(this, CHANNEL_ID)
//            .setSmallIcon(
//                if (currentMode == MODE_ANCHOR)
//                    android.R.drawable.ic_btn_speak_now
//                else
//                    android.R.drawable.ic_lock_silent_mode_off
//            )
//            .setContentTitle(title)
//            .setContentText(content)
//            .setOngoing(true)
//            .setContentIntent(pi)
//            .build()
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                CHANNEL_ID,
//                getString(R.string.voice_service_channel_name),
//                NotificationManager.IMPORTANCE_LOW
//            )
//            getSystemService(NotificationManager::class.java)
//                ?.createNotificationChannel(channel)
//        }
//    }
//
//    // ================= 工具 =================
//
//    private fun hasMicPermission(): Boolean {
//        return ActivityCompat.checkSelfPermission(
//            this,
//            Manifest.permission.RECORD_AUDIO
//        ) == PackageManager.PERMISSION_GRANTED
//    }
//
//    private fun isAppInBackground(): Boolean {
//        return !VoiceKeepApp.isForeground
//    }
//
//    override fun onDestroy() {
//        silentPlayer.stop()
//        releaseWakeLock()
//        instance = null
//        stopForeground(true)
//        super.onDestroy()
//    }
//}
