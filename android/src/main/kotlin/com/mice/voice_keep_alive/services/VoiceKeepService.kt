package com.mice.voice_keep_alive.services

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
    private var focusRequest: AudioFocusRequest? = null

    private var currentMode: Int = MODE_AUDIENCE
    private var title: String = ""
    private var content: String = ""

    // For "虚拟录音"（仅在特定设备/系统使用）
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    @Volatile private var isRecording = false

    // For 静音播放保活（通用）
    private var mediaPlayer: MediaPlayer? = null

    // whether current keep-alive strategy uses microphone (虚拟录音)
    private var usingFakeRecord = false

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

        // request audio focus only when anchor wants "real" audio focus (keep minimal)
        if (currentMode == MODE_ANCHOR) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                stopSelf()
                return START_NOT_STICKY
            }
            requestAudioFocus()
        }

        // Build pending intent to resume activity
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title.ifEmpty { getString(R.string.voice_service_title) })
            .setContentText(
                if (content.isNotEmpty()) content
                else if (currentMode == MODE_ANCHOR) getString(R.string.voice_service_text)
                else getString(R.string.voice_service_play_text)
            )
            .setSmallIcon(
                if (currentMode == MODE_ANCHOR) android.R.drawable.ic_btn_speak_now
                else android.R.drawable.ic_lock_silent_mode_off
            )
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        // Decide keep-alive strategy per-device / per-version
        val isMiui = isMiuiManufacturer()
        val sdk = Build.VERSION.SDK_INT

        // Only use microphone-type foreground when we will actually keep audio input alive (redmi Android15 scenario)
        if (sdk >= 35 && isMiui && currentMode == MODE_ANCHOR) {
            // Android 15 + Xiaomi/Redmi + Anchor -> use virtual recording trick
            usingFakeRecord = true
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

            startFakeRecording()
        } else {
            // Other phones or audience mode -> use silent playback (won't occupy mic)
            usingFakeRecord = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
                return START_NOT_STICKY
            }

            startSilentPlayback()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopKeepAlive()
        releaseWakeLock()
        abandonAudioFocus()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------ public helpers for cooperating with real recording ------------------
    /**
     * Call this **before** the app/rtc/anchor starts real microphone recording.
     * It will stop the service's fake-recording or silent player so real recorder can obtain mic.
     */
    fun pauseKeepAliveForRealRecording() {
        if (usingFakeRecord) {
            stopFakeRecording()
        } else {
            stopSilentPlayback()
        }
    }

    /**
     * Call this after the real recording stops, to resume keep-alive strategy.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun resumeKeepAliveAfterRealRecording() {
        // Only resume if service still running and mode is anchor
        if (currentMode != MODE_ANCHOR) return
        val isMiui = isMiuiManufacturer()
        val sdk = Build.VERSION.SDK_INT
        if (sdk >= 35 && isMiui) {
            usingFakeRecord = true
            startFakeRecording()
        } else {
            usingFakeRecord = false
            startSilentPlayback()
        }
    }

    /**
     * Fully stop both strategies.
     */
    private fun stopKeepAlive() {
        stopFakeRecording()
        stopSilentPlayback()
    }

    // ------------------ WakeLock ------------------
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoiceKeepService::WakeLock"
            )
        }
        if (wakeLock?.isHeld == false) wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    // ------------------ Notification Channel ------------------
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

    // ------------------ Audio Focus ------------------
    private fun requestAudioFocus() {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { }
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioManager?.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    // ------------------ Silent playback keep-alive ------------------
    private fun startSilentPlayback() {
        if (mediaPlayer != null) return
        try {
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attr)
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
        } catch (ignored: Exception) { }
    }

    // ------------------ Fake recording (Android15/MIUI 用) ------------------
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startFakeRecording() {
        if (isRecording) return
        // Guard permission (redundant if checked earlier)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val sampleRate = 8000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate * 2)

        try {
            val source = MediaRecorder.AudioSource.VOICE_COMMUNICATION

            audioRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use builder on API >= M for better configurability
                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setSampleRate(sampleRate)
                    .build()

                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            isRecording = true
            audioRecord?.startRecording()

            recordThread = Thread {
                val buffer = ByteArray(bufferSize)
                try {
                    while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = try {
                            audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        } catch (t: Throwable) {
                            0
                        }
                        // discard data - but keep reading to keep the mic path active
                        // add a small sleep to avoid busy-loop and to avoid hogging CPU
                        try {
                            Thread.sleep(250) // 心跳式读取，250ms 间隔
                        } catch (ie: InterruptedException) {
                            break
                        }
                        // If reading stopped unexpectedly, try to restart once
                        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                            // attempt to restart
                            try {
                                audioRecord?.startRecording()
                            } catch (e: Exception) {
                                // failed, break loop and cleanup
                                break
                            }
                        }
                    }
                } finally {
                    // ensure cleanup if thread exits
                    stopFakeRecording()
                }
            }.apply { isDaemon = true; start() }
        } catch (e: Exception) {
            e.printStackTrace()
            stopFakeRecording()
        }
    }

    private fun stopFakeRecording() {
        isRecording = false
        try {
            recordThread?.interrupt()
            recordThread = null
            audioRecord?.let {
                try {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        it.stop()
                    }
                } catch (ignored: Exception) { }
                try { it.release() } catch (ignored: Exception) { }
            }
            audioRecord = null
        } catch (ignored: Exception) { }
    }

    // ------------------ Utils ------------------
    private fun isMiuiManufacturer(): Boolean {
        val manu = Build.MANUFACTURER ?: ""
        return manu.equals("Xiaomi", ignoreCase = true) || manu.equals("Redmi", ignoreCase = true)
    }
}

//package com.mice.voice_keep_alive.services
//
//import android.Manifest
//import android.app.*
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.content.pm.ServiceInfo
//import android.media.*
//import android.os.Build
//import android.os.IBinder
//import android.os.PowerManager
//import androidx.annotation.RequiresPermission
//import androidx.core.app.NotificationCompat
//import androidx.core.content.ContextCompat
//import com.mice.voice_keep_alive.R
//import com.mice.voice_keep_alive.utils.ContextActivityKeeper
//
//public class VoiceKeepService : Service() {
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
//    private var focusRequest: AudioFocusRequest? = null
//    private var currentMode: Int = MODE_AUDIENCE
//    private var title: String = ""
//    private var content: String = ""
//
//    private var audioRecord: AudioRecord? = null
//    private var recordThread: Thread? = null
//    private var isRecording = false
//
//    override fun onCreate() {
//        super.onCreate()
//        createNotificationChannel()
//        acquireWakeLock()
//    }
//
//    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        currentMode = intent?.getIntExtra("mode", MODE_AUDIENCE) ?: MODE_AUDIENCE
//        title = intent?.getStringExtra("title") ?: ""
//        content = intent?.getStringExtra("content") ?: ""
//
//        if (currentMode == MODE_ANCHOR) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
//                != PackageManager.PERMISSION_GRANTED
//            ) {
//                stopSelf()
//                return START_NOT_STICKY
//            }
//            requestAudioFocus()
//        }
//
//        // 安全获取 Activity 或备用 Intent
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
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
//            )
//        }
//
//        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle(title.ifEmpty { getString(R.string.voice_service_title) })
//            .setContentText(
//                if (content.isNotEmpty()) content
//                else if (currentMode == MODE_ANCHOR) getString(R.string.voice_service_text)
//                else getString(R.string.voice_service_play_text)
//            )
//            .setSmallIcon(
//                if (currentMode == MODE_ANCHOR) android.R.drawable.ic_btn_speak_now
//                else android.R.drawable.ic_lock_silent_mode_off
//            )
//            .setOngoing(true)
//            .setContentIntent(pendingIntent)
//            .build()
//
//        try {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                val serviceType = if (currentMode == MODE_ANCHOR)
//                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
//                else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
//
//                startForeground(NOTIFICATION_ID, notification, serviceType)
//            } else {
//                startForeground(NOTIFICATION_ID, notification)
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            stopSelf()
//            return START_NOT_STICKY
//        }
//
//        if (currentMode == MODE_ANCHOR) startRecording()
//        return START_STICKY
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        stopRecording()
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
//            wakeLock = pm.newWakeLock(
//                PowerManager.PARTIAL_WAKE_LOCK,
//                "VoiceKeepService::WakeLock"
//            )
//        }
//        if (wakeLock?.isHeld == false) wakeLock?.acquire()
//    }
//
//    private fun releaseWakeLock() {
//        if (wakeLock?.isHeld == true) wakeLock?.release()
//    }
//
//    /** ---------- Notification ---------- */
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
//        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
//        val audioAttributes = AudioAttributes.Builder()
//            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
//            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
//            .build()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
//                .setAudioAttributes(audioAttributes)
//                .setOnAudioFocusChangeListener { }
//                .setAcceptsDelayedFocusGain(true)
//                .build()
//            audioManager?.requestAudioFocus(focusRequest!!)
//        } else {
//            @Suppress("DEPRECATION")
//            audioManager?.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
//        }
//    }
//
//    private fun abandonAudioFocus() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
//        } else {
//            @Suppress("DEPRECATION")
//            audioManager?.abandonAudioFocus(null)
//        }
//    }
//
//    /** ---------- 麦克风采集 ---------- */
//    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
//    private fun startRecording() {
//        if (isRecording) return
//        val sampleRate = 16000
//        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
//
//        audioRecord = AudioRecord(
//            MediaRecorder.AudioSource.MIC,
//            sampleRate,
//            AudioFormat.CHANNEL_IN_MONO,
//            AudioFormat.ENCODING_PCM_16BIT,
//            bufferSize
//        )
//
//        isRecording = true
//        audioRecord?.startRecording()
//
//        recordThread = Thread {
//            val buffer = ByteArray(bufferSize)
//            while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
//                audioRecord?.read(buffer, 0, buffer.size)
//            }
//        }
//        recordThread?.start()
//    }
//
//    private fun stopRecording() {
//        isRecording = false
//        recordThread?.interrupt()
//        recordThread = null
//        audioRecord?.stop()
//        audioRecord?.release()
//        audioRecord = null
//    }
//}