package com.mice.voice_keep_alive.services

import android.Manifest
import android.R
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mice.voice_keep_alive.utils.ContextActivityKeeper

class VoiceKeepService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_service_channel"
        const val NOTIFICATION_ID = 1001
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        requestAudioFocus()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 13+ 权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        // ✅ 这里创建一个明确的 Activity intent（注意用 this@CustomVoiceService）
        val activityIntent = Intent(this@VoiceKeepService, ContextActivityKeeper.activity!!.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("openPage", "voiceRoom")
        }
        val pendingIntent = PendingIntent.getActivity(
            this@VoiceKeepService,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // 构建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(com.mice.voice_keep_alive.R.string.voice_service_title))
            .setContentText(getString(com.mice.voice_keep_alive.R.string.voice_service_text))
            .setSmallIcon(R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 指定前台服务类型为麦克风
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }


    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        abandonAudioFocus()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** ---------- WakeLock 保持 CPU 运行 ---------- */
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CustomVoiceService::WakeLock"
            )
        }
        // 无限保持，直到手动释放
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
//    private fun releaseWakeLock() {
////        wakeLock?.release()
//        wakeLock?.let {
//            if (it.isHeld) it.release()
//        }
//    }

    /** ---------- Notification ---------- */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(com.mice.voice_keep_alive.R.string.voice_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /** ---------- AudioFocus 请求 ---------- */
    private fun requestAudioFocus() {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            // 恢复播放/恢复麦克风采集
                        }
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            // 永久失去焦点，应该停止播放或关闭麦克风
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            // 临时失去焦点，先暂停
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            // 可以降低音量继续播放
                        }
                    }
                }
                .setAcceptsDelayedFocusGain(true) // 可选
                .build()

            audioManager?.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let {
                audioManager?.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }
}

//package com.chat.shila.services
//
//import android.Manifest
//import android.app.*
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.content.pm.ServiceInfo
//import android.media.*
//import android.os.Build
//import android.os.IBinder
//import android.os.PowerManager
//import androidx.annotation.RequiresApi
//import androidx.core.app.NotificationCompat
//import androidx.core.content.ContextCompat
//
//class CustomVoiceService : Service() {
//
//    companion object {
//        const val CHANNEL_ID = "voice_service_channel"
//        const val NOTIFICATION_ID = 1001
//    }
//
//    private var wakeLock: PowerManager.WakeLock? = null
//    private var audioManager: AudioManager? = null
//
//    override fun onCreate() {
//        super.onCreate()
//        createNotificationChannel()
//        acquireWakeLock()
//        requestAudioFocus()
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        // Android 13+ 权限检查
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
//                != PackageManager.PERMISSION_GRANTED
//            ) {
//                stopSelf()
//                return START_NOT_STICKY
//            }
//        }
//
//        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("语音通话进行中")
//            .setContentText("保持后台运行，确保语音不中断")
//            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
//            .setOngoing(true)
//            .build()
//
//        try {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                startForeground(
//                    NOTIFICATION_ID,
//                    notification,
//                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
//                )
//            } else {
//                startForeground(NOTIFICATION_ID, notification)
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            stopSelf()
//            return START_NOT_STICKY
//        }
//
//        return START_STICKY
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        releaseWakeLock()
//        abandonAudioFocus()
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    /** ---------- WakeLock 保持 CPU 运行 ---------- */
//    private fun acquireWakeLock() {
//        if (wakeLock == null) {
//            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
//            wakeLock = pm.newWakeLock(
//                PowerManager.PARTIAL_WAKE_LOCK,
//                "CustomVoiceService::WakeLock"
//            )
//        }
//        if (wakeLock?.isHeld == false) {
//            wakeLock?.acquire()
//        }
//    }
//
//    private fun releaseWakeLock() {
//        if (wakeLock?.isHeld == true) {
//            wakeLock?.release()
//        }
//    }
//
//    /** ---------- Notification ---------- */
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                CHANNEL_ID,
//                "语音后台服务",
//                NotificationManager.IMPORTANCE_LOW
//            )
//            val manager = getSystemService(NotificationManager::class.java)
//            manager?.createNotificationChannel(channel)
//        }
//    }
//
//    /** ---------- AudioFocus 请求 ---------- */
//    private fun requestAudioFocus() {
//        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
//        val audioAttributes = AudioAttributes.Builder()
//            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
//            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
//            .build()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
//                .setAudioAttributes(audioAttributes)
//                .setOnAudioFocusChangeListener { /* ignore */ }
//                .build()
//            audioManager?.requestAudioFocus(focusRequest)
//        } else {
//            audioManager?.requestAudioFocus(
//                null,
//                AudioManager.STREAM_VOICE_CALL,
//                AudioManager.AUDIOFOCUS_GAIN
//            )
//        }
//    }
//
//    private fun abandonAudioFocus() {
//        audioManager?.abandonAudioFocus(null)
//    }
//}