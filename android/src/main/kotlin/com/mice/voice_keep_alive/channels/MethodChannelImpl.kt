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

class MethodChannelImpl: MethodChannel.MethodCallHandler {
    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        when (call.method) {
            "startService" -> {
                val mode = call.argument<Int>("mode") ?: VoiceKeepService.MODE_AUDIENCE  // 从 Flutter 传过来的参数

                // 如果是主播模式，需要检查麦克风权限
                if (mode == VoiceKeepService.MODE_ANCHOR &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    ContextCompat.checkSelfPermission(
                        ContextActivityKeeper.activity!!,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    result.error("PERMISSION_DENIED", "麦克风权限未授予", null)
                    return
                }
                try {
                    val intent = Intent(ContextActivityKeeper.activity, VoiceKeepService::class.java).apply {
                        putExtra("mode", mode)  // ✅ 把 mode 传给 Service
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextActivityKeeper.activity?.startForegroundService(intent)
                    } else {
                        ContextActivityKeeper.activity?.startService(intent)
                    }
                    println("开启服务$mode")
                    result.success(null)
                } catch (e: Exception) {
                    e.printStackTrace()
                    result.error("SERVICE_ERROR", e.message, null)
                }
            }
            "stopService" -> {
                println("关闭服务")
                try {
                    val intent = Intent(ContextActivityKeeper.activity, VoiceKeepService::class.java)
                    ContextActivityKeeper.activity?.stopService(intent)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("SERVICE_ERROR", e.message, null)
                }
            }
            else -> result.notImplemented()
        }
    }
}