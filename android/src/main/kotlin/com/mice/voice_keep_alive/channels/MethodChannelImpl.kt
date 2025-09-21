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
                println("开启服务")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    ContextCompat.checkSelfPermission(ContextActivityKeeper.activity!!, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    result.error("PERMISSION_DENIED", "麦克风权限未授予", null)
                    return
                }
                try {
                    val intent = Intent(ContextActivityKeeper.activity, VoiceKeepService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextActivityKeeper.activity?.startForegroundService(intent)
                    } else {
                        ContextActivityKeeper.activity?.startService(intent)
                    }
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