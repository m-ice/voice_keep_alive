package com.mice.voice_keep_alive.channels

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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
                // 从 Flutter 传过来的参数
                val mode = call.argument<Int>("mode") ?: VoiceKeepService.MODE_AUDIENCE
                val title = call.argument<String>("title") ?: ""
                val content = call.argument<String>("content") ?: ""
                val roomParams = call.argument<String>("roomParams") ?: ""
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
                    val intent =
                        Intent(ContextActivityKeeper.activity, VoiceKeepService::class.java).apply {
                            putExtra("mode", mode)
                            putExtra("title", title)
                            putExtra("content", content)
                            putExtra("roomParams", roomParams)
                        }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextActivityKeeper.activity?.startForegroundService(intent)
                    } else {
                        ContextActivityKeeper.activity?.startService(intent)
                    }
                    println("开启服务$mode-$title-$content")
                    result.success(null)
                } catch (e: Exception) {
                    e.printStackTrace()
                    result.error("SERVICE_ERROR", e.message, null)
                }
            }

            "stopService" -> {
                println("关闭服务")
                try {
                    val intent =
                        Intent(ContextActivityKeeper.activity, VoiceKeepService::class.java)
                    ContextActivityKeeper.activity?.stopService(intent)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("SERVICE_ERROR", e.message, null)
                }
            }
            "setAudioActive" -> {
                val active = call.argument<Boolean>("active") ?: false
                if (active != ContextActivityKeeper.lastActive) {
                    ContextActivityKeeper.lastActive = active
                    VoiceKeepService.instance?.onAudioStateChanged(active)
                        ?: run {
                            println("VoiceKeepService 实例为 null")
                        }
                }
                result.success(null)
            }
            "moveAppToBackground" -> {
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
                        Log.e("AndroidBackPlugin", "No valid context or activity found")
                        result.success(false)
                    }
                } catch (e: Exception) {
                    Log.e("AndroidBackPlugin", "moveAppToBackground failed", e)
                    result.success(false)
                }
            }


            else -> result.notImplemented()
        }
    }
}