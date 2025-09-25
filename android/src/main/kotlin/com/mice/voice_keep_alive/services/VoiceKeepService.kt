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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mice.voice_keep_alive.utils.ContextActivityKeeper
import com.mice.voice_keep_alive.R

class VoiceKeepService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_service_channel"
        const val NOTIFICATION_ID = 1001

        const val MODE_AUDIENCE = 0  // 观众模式：只播放
        const val MODE_ANCHOR = 1    // 主播模式：采集+播放
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var currentMode: Int = MODE_AUDIENCE
    private var title: String = ""
    private var content: String = ""
    // --- 麦克风采集 (保活用) ---
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private var isRecording = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        requestAudioFocus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 获取模式（默认观众）
        currentMode = intent?.getIntExtra("mode", MODE_AUDIENCE) ?: MODE_AUDIENCE
        title = intent?.getStringExtra("title") ?: ""
        content = intent?.getStringExtra("content") ?: ""
        // Android 13+ 麦克风权限检查（只有主播模式才需要）
        if (currentMode == MODE_ANCHOR &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Activity intent
        val activityIntent = Intent(this, ContextActivityKeeper.activity!!.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("openPage", "voiceRoom")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // 构建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                title.ifEmpty { getString(R.string.voice_service_title) })
            .setContentText(
                if(content.isNotEmpty())
                    content
                else if (currentMode == MODE_ANCHOR)
                    getString(R.string.voice_service_text)
                else
                    getString(R.string.voice_service_play_text)
            )
            .setSmallIcon(  if (currentMode == MODE_ANCHOR)
                android.R.drawable.ic_btn_speak_now
            else
                android.R.drawable.ic_lock_silent_mode_off
            )
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = when (currentMode) {
                    MODE_ANCHOR -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        // 启动录音线程 (仅主播模式)
        if (currentMode == MODE_ANCHOR) {
            startRecording()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
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
                "VoiceKeepService::WakeLock"
            )
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    /** ---------- Notification ---------- */
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

    /** ---------- 麦克风采集 (Anchor 模式保活) ---------- */
    private fun startRecording() {
        if (isRecording) return

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        isRecording = true
        audioRecord?.startRecording()

        recordThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // 丢弃数据，只是保持 MIC 活跃
                }
            }
        }
        recordThread?.start()
    }

    private fun stopRecording() {
        isRecording = false
        recordThread?.interrupt()
        recordThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}