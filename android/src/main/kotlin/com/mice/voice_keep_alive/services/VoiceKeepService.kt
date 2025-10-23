package com.mice.voice_keep_alive.services

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.mice.voice_keep_alive.R
import com.mice.voice_keep_alive.utils.ContextActivityKeeper

public class VoiceKeepService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_service_channel"
        const val NOTIFICATION_ID = 1001
        const val MODE_AUDIENCE = 0
        const val MODE_ANCHOR = 1
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var currentMode: Int = MODE_AUDIENCE
    private var title: String = ""
    private var content: String = ""
    private var mediaPlayer: MediaPlayer? = null
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private var isRecording = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentMode = intent?.getIntExtra("mode", MODE_AUDIENCE) ?: MODE_AUDIENCE
        title = intent?.getStringExtra("title") ?: ""
        content = intent?.getStringExtra("content") ?: ""

        val activityIntent: Intent? = ContextActivityKeeper.activity?.let {
            Intent(this, it.javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("openPage", "voiceRoom")
            }
        } ?: packageManager.getLaunchIntentForPackage(packageName)

        val pendingIntent = activityIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                            PendingIntent.FLAG_IMMUTABLE else 0
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title.ifEmpty { getString(R.string.voice_service_title) })
            .setContentText(
                if (content.isNotEmpty()) content
                else if (currentMode == MODE_ANCHOR)
                    getString(R.string.voice_service_text)
                else
                    getString(R.string.voice_service_play_text)
            )
            .setSmallIcon(
                if (currentMode == MODE_ANCHOR)
                    android.R.drawable.ic_btn_speak_now
                else
                    android.R.drawable.ic_lock_silent_mode_off
            )
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        // 根据系统环境自动选择保活策略
        if (currentMode == MODE_ANCHOR) {
            startSilentPlayback()
//            if (Build.VERSION.SDK_INT >= 34) { startFakeRecording() } else { startSilentPlayback() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSilentPlayback()
        stopFakeRecording()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** ---------- 唤醒锁 ---------- */
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceKeepService::WakeLock")
        }
        if (wakeLock?.isHeld == false){
            try {
                wakeLock?.acquire()
            } catch (_: Exception) {}
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    /** ---------- 通知通道 ---------- */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.voice_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /** ---------- 静音播放防休眠 ---------- */
    private fun startSilentPlayback() {
        if (mediaPlayer != null) return
        try {
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attr)
                setOnErrorListener { _, _, _ ->
                    stopSilentPlayback()
                    true
                }
                val afd = resources.openRawResourceFd(R.raw.silence)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setVolume(0f, 0f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSilentPlayback()
        }
    }

    private fun stopSilentPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {
        }
    }

    /** ---------- 虚拟录音保活 ---------- */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startFakeRecording() {
        if (isRecording) return
        try {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()
            isRecording = true

            recordThread = Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.read(buffer, 0, buffer.size)
                }
            }
            recordThread?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            stopFakeRecording()
        }
    }

    private fun stopFakeRecording() {
        try {
            isRecording = false
            recordThread?.interrupt()
            recordThread = null
            audioRecord?.let {
                try {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        it.stop()
                    }
                } catch (_: Exception) {}
                try { it.release() } catch (_: Exception) {}
            }
            audioRecord = null
        } catch (_: Exception) {}
    }

    /** ---------- 机型识别 ---------- */
    private fun isMiuiManufacturer(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
    }
}










//package com.mice.voice_keep_alive.services
//
//import android.app.*
//import android.content.Intent
//import android.media.*
//import android.os.Build
//import android.os.IBinder
//import android.os.PowerManager
//import androidx.core.app.NotificationCompat
//import com.mice.voice_keep_alive.R
//import com.mice.voice_keep_alive.utils.ContextActivityKeeper
//
//class VoiceKeepService : Service() {
//
//    companion object {
//        const val CHANNEL_ID = "voice_service_channel"
//        const val NOTIFICATION_ID = 1001
//        const val MODE_AUDIENCE = 0
//        const val MODE_ANCHOR = 1
//    }
//
//    private var wakeLock: PowerManager.WakeLock? = null
//    private var audioManager: AudioManager? = null
//    private var currentMode: Int = MODE_AUDIENCE
//    private var title: String = ""
//    private var content: String = ""
//    private var mediaPlayer: MediaPlayer? = null
//
//    override fun onCreate() {
//        super.onCreate()
//        createNotificationChannel()
//        acquireWakeLock()
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        currentMode = intent?.getIntExtra("mode", MODE_AUDIENCE) ?: MODE_AUDIENCE
//        title = intent?.getStringExtra("title") ?: ""
//        content = intent?.getStringExtra("content") ?: ""
//
//        if (currentMode == MODE_ANCHOR) {
//            requestAudioFocus()
//        }
//
//        val activityIntent: Intent? = ContextActivityKeeper.activity?.let {
//            Intent(this, it.javaClass).apply {
//                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                putExtra("openPage", "voiceRoom")
//            }
//        } ?: packageManager.getLaunchIntentForPackage(packageName)
//
//        val pendingIntent = activityIntent?.let {
//            PendingIntent.getActivity(
//                this,
//                0,
//                it,
//                PendingIntent.FLAG_UPDATE_CURRENT or
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
//                            PendingIntent.FLAG_IMMUTABLE else 0
//            )
//        }
//
//        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle(title.ifEmpty { getString(R.string.voice_service_title) })
//            .setContentText(
//                if (content.isNotEmpty()) content
//                else if (currentMode == MODE_ANCHOR)
//                    getString(R.string.voice_service_text)
//                else
//                    getString(R.string.voice_service_play_text)
//            )
//            .setSmallIcon(
//                if (currentMode == MODE_ANCHOR)
//                    android.R.drawable.ic_btn_speak_now
//                else
//                    android.R.drawable.ic_lock_silent_mode_off
//            )
//            .setOngoing(true)
//            .setContentIntent(pendingIntent)
//            .build()
//
//        startForeground(NOTIFICATION_ID, notification)
//
//        startSilentPlayback()
//        return START_STICKY
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        stopSilentPlayback()
//        releaseWakeLock()
//        abandonAudioFocus()
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    /** ---------- WakeLock ---------- */
//    private fun acquireWakeLock() {
//        if (wakeLock == null) {
//            val pm = getSystemService(POWER_SERVICE) as PowerManager
//            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceKeepService::WakeLock")
//        }
//        if (wakeLock?.isHeld == false) wakeLock?.acquire()
//    }
//
//    private fun releaseWakeLock() {
//        if (wakeLock?.isHeld == true) wakeLock?.release()
//    }
//
//    /** ---------- Notification Channel ---------- */
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                CHANNEL_ID,
//                getString(R.string.voice_service_channel_name),
//                NotificationManager.IMPORTANCE_LOW
//            )
//            val manager = getSystemService(NotificationManager::class.java)
//            manager?.createNotificationChannel(channel)
//        }
//    }
//
//    /** ---------- AudioFocus ---------- */
//    private fun requestAudioFocus() {
//        try {
//            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
//            @Suppress("DEPRECATION")
//            audioManager?.requestAudioFocus(
//                null,
//                AudioManager.STREAM_VOICE_CALL,
//                AudioManager.AUDIOFOCUS_GAIN
//            )
//        } catch (_: Exception) { }
//    }
//
//    private fun abandonAudioFocus() {
//        try {
//            @Suppress("DEPRECATION")
//            audioManager?.abandonAudioFocus(null)
//        } catch (_: Exception) { }
//    }
//
//    /** ---------- 静音播放防休眠 ---------- */
//    private fun startSilentPlayback() {
//        if (mediaPlayer != null) return
//        try {
//            val attr = AudioAttributes.Builder()
//                .setUsage(AudioAttributes.USAGE_MEDIA)
//                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                .build()
//
//            mediaPlayer = MediaPlayer().apply {
//                setAudioAttributes(attr)
//                val afd = resources.openRawResourceFd(R.raw.silence)
//                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
//                afd.close()
//                isLooping = true
//                setVolume(0f, 0f)
//                prepare()
//                start()
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            stopSilentPlayback()
//        }
//    }
//
//    private fun stopSilentPlayback() {
//        try {
//            mediaPlayer?.stop()
//            mediaPlayer?.release()
//            mediaPlayer = null
//        } catch (_: Exception) { }
//    }
//}