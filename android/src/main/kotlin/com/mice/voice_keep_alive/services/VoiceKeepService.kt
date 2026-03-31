package com.mice.voice_keep_alive.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.mice.voice_keep_alive.R

class VoiceKeepService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_service_channel"
        const val NOTIFICATION_ID = 1001

        const val MODE_AUDIENCE = 0
        const val MODE_ANCHOR = 1

        const val EXTRA_MODE = "mode"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_ROOM_PARAMS = "roomParams"
        const val EXTRA_IN_ROOM = "inRoom"

        @Volatile
        var instance: VoiceKeepService? = null
    }

    private var currentMode = MODE_AUDIENCE
    private var inRoom = false

    private var title = ""
    private var content = ""
    private var roomParams = ""

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentMode = intent?.getIntExtra(EXTRA_MODE, MODE_AUDIENCE) ?: MODE_AUDIENCE
        title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.voice_service_title)
        content = intent?.getStringExtra(EXTRA_CONTENT) ?: getString(R.string.voice_service_text)
        roomParams = intent?.getStringExtra(EXTRA_ROOM_PARAMS) ?: ""
        inRoom = intent?.getBooleanExtra(EXTRA_IN_ROOM, true) ?: true

        if (!inRoom) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        startOrUpdateForeground()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateState(
        newMode: Int,
        newInRoom: Boolean,
        newTitle: String?,
        newContent: String?,
        newRoomParams: String?
    ) {
        currentMode = newMode
        inRoom = newInRoom

        if (!newTitle.isNullOrBlank()) title = newTitle
        if (!newContent.isNullOrBlank()) content = newContent
        if (!newRoomParams.isNullOrBlank()) roomParams = newRoomParams

        if (!inRoom) {
            stopSelfSafely()
            return
        }

        startOrUpdateForeground()
    }

    private fun startOrUpdateForeground() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                foregroundServiceType()
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                    if (currentMode == MODE_ANCHOR && hasMicPermission()) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        0
                    }
        } else {
            // 旧版本没有 microphone type，保持 mediaPlayback 即可
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, Class.forName("$packageName.MainActivity"))

        launchIntent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        launchIntent.putExtra("roomParams", roomParams)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smallIcon = if (currentMode == MODE_ANCHOR) {
            android.R.drawable.ic_btn_speak_now
        } else {
            android.R.drawable.ic_lock_silent_mode_off
        }

        val finalTitle = if (title.isBlank()) {
            getString(R.string.voice_service_title)
        } else {
            title
        }

        val finalContent = if (content.isBlank()) {
            if (currentMode == MODE_ANCHOR) {
                "语音房通话中（可发言）"
            } else {
                "语音房收听中"
            }
        } else {
            content
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(finalTitle)
            .setContentText(finalContent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.voice_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            nm?.createNotificationChannel(channel)
        }
    }

    private fun hasMicPermission(): Boolean {
        val recordAudioGranted =
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        val fgsMicGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        return recordAudioGranted && fgsMicGranted
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}