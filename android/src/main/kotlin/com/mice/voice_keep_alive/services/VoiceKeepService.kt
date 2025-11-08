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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Step 1: 立即创建并显示通知（尽早调用）
        val notification = buildForegroundNotification(intent)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        // Step 2: 异步执行其余逻辑（防止阻塞）
        Thread {
            try {
                handleIntentWork(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        return START_STICKY
    }

    /** 构建通知 */
    private fun buildForegroundNotification(intent: Intent?): Notification {
        val mode = intent?.getIntExtra("mode", MODE_AUDIENCE) ?: MODE_AUDIENCE
        val title = intent?.getStringExtra("title") ?: getString(R.string.voice_service_title)
        val content = intent?.getStringExtra("content") ?: getString(R.string.voice_service_text)

        // 确保通知通道存在
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.voice_service_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                manager?.createNotificationChannel(channel)
            }
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(
                if (mode == MODE_ANCHOR)
                    android.R.drawable.ic_btn_speak_now
                else
                    android.R.drawable.ic_lock_silent_mode_off
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /** 异步处理逻辑 */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun handleIntentWork(intent: Intent?) {
        currentMode = intent?.getIntExtra("mode", MODE_AUDIENCE) ?: MODE_AUDIENCE
        acquireWakeLock()

        if (currentMode == MODE_ANCHOR) {
            startSilentPlayback()
            // or startFakeRecording()
        } else {
            stopSilentPlayback()
            stopFakeRecording()
        }
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

            val player = MediaPlayer()
            player.setAudioAttributes(attr)
            player.isLooping = true
            player.setVolume(0f, 0f)

            // 加载资源
            val afd = resources.openRawResourceFd(R.raw.silence)
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            // 异步准备，防止卡死 native
            player.setOnPreparedListener { mp ->
                try {
                    mp.start()
                    mediaPlayer = mp
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopSilentPlayback()
                }
            }

            player.setOnErrorListener { mp, what, extra ->
                android.util.Log.e("VoiceKeepService", "MediaPlayer error: $what / $extra")
                stopSilentPlayback()
                true
            }

            player.prepareAsync()

            // 启动 10 秒超时保护
            android.os.Handler(mainLooper).postDelayed({
                if (mediaPlayer == null || mediaPlayer?.isPlaying == false) {
                    android.util.Log.w("VoiceKeepService", "MediaPlayer prepare timeout, restarting")
                    stopSilentPlayback()
                    startSilentPlayback()
                }
            }, 10000)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSilentPlayback()
        }
    }

    private fun stopSilentPlayback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) { }
        mediaPlayer = null
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