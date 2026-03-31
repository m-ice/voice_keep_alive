package com.mice.voice_keep_alive.channels

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mice.voice_keep_alive.services.VoiceKeepService
import com.mice.voice_keep_alive.utils.ContextActivityKeeper
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MethodChannelImpl : MethodChannel.MethodCallHandler {

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startService" -> startService(call, result)
            "updateServiceState" -> updateServiceState(call, result)
            "stopService" -> stopService(result)
            "moveAppToBackground" -> moveAppToBackground(result)
            else -> result.notImplemented()
        }
    }

    private fun startService(call: MethodCall, result: MethodChannel.Result) {
        val ctx = ContextActivityKeeper.activity ?: ContextActivityKeeper.context
        if (ctx == null) {
            result.error("NO_CONTEXT", "没有可用的 Context", null)
            return
        }

        val mode = call.argument<Int>("mode") ?: VoiceKeepService.MODE_AUDIENCE
        val title = call.argument<String>("title") ?: ""
        val content = call.argument<String>("content") ?: ""
        val roomParams = call.argument<String>("roomParams") ?: ""
        val inRoom = call.argument<Boolean>("inRoom") ?: true

        if (mode == VoiceKeepService.MODE_ANCHOR && !hasRecordAudioPermission()) {
            result.error("PERMISSION_DENIED", "麦克风权限未授予", null)
            return
        }

        try {
            val intent = Intent(ctx, VoiceKeepService::class.java).apply {
                putExtra(VoiceKeepService.EXTRA_MODE, mode)
                putExtra(VoiceKeepService.EXTRA_TITLE, title)
                putExtra(VoiceKeepService.EXTRA_CONTENT, content)
                putExtra(VoiceKeepService.EXTRA_ROOM_PARAMS, roomParams)
                putExtra(VoiceKeepService.EXTRA_IN_ROOM, inRoom)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(ctx, intent)
            } else {
                ctx.startService(intent)
            }

            result.success(true)
        } catch (e: Exception) {
            result.error("SERVICE_ERROR", e.message, null)
        }
    }

    private fun updateServiceState(call: MethodCall, result: MethodChannel.Result) {
        val service = VoiceKeepService.instance
        if (service == null) {
            result.error("SERVICE_NULL", "VoiceKeepService 未启动", null)
            return
        }

        val mode = call.argument<Int>("mode") ?: VoiceKeepService.MODE_AUDIENCE
        val inRoom = call.argument<Boolean>("inRoom") ?: true
        val title = call.argument<String>("title")
        val content = call.argument<String>("content")
        val roomParams = call.argument<String>("roomParams")

        if (mode == VoiceKeepService.MODE_ANCHOR && !hasRecordAudioPermission()) {
            result.error("PERMISSION_DENIED", "麦克风权限未授予", null)
            return
        }

        try {
            service.updateState(
                newMode = mode,
                newInRoom = inRoom,
                newTitle = title,
                newContent = content,
                newRoomParams = roomParams
            )
            result.success(true)
        } catch (e: Exception) {
            result.error("SERVICE_ERROR", e.message, null)
        }
    }

    private fun stopService(result: MethodChannel.Result) {
        val ctx = ContextActivityKeeper.activity ?: ContextActivityKeeper.context
        if (ctx == null) {
            result.error("NO_CONTEXT", "No context available", null)
            return
        }

        try {
            val intent = Intent(ctx, VoiceKeepService::class.java)
            ctx.stopService(intent)
            result.success(true)
        } catch (e: Exception) {
            result.error("SERVICE_ERROR", e.message, null)
        }
    }

    private fun moveAppToBackground(result: MethodChannel.Result) {
        try {
            val activity = ContextActivityKeeper.activity
            val context = ContextActivityKeeper.context

            if (activity != null) {
                activity.moveTaskToBack(false)
                result.success(true)
            } else if (context != null) {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                result.success(true)
            } else {
                result.success(false)
            }
        } catch (_: Exception) {
            result.success(false)
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        val ctx = ContextActivityKeeper.activity ?: ContextActivityKeeper.context ?: return false
        return ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}