package com.mice.voice_keep_alive

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.mice.voice_keep_alive.utils.AppUtil
import com.mice.voice_keep_alive.utils.ContextActivityKeeper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodChannel

class VoiceKeepAlivePlugin : FlutterPlugin, ActivityAware {

  private lateinit var channel: MethodChannel
  private var context: Context? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    ContextActivityKeeper.context = context
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "voice_keep_alive")

    // ✅ 改为匿名实现，替代原来的 MethodChannelImpl
    channel.setMethodCallHandler { call, result ->
      when (call.method) {
        "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")
        else -> result.notImplemented()
      }
    }

    AppUtil.appHandler = Handler(Looper.getMainLooper())
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    context = null
    ContextActivityKeeper.clear()
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    ContextActivityKeeper.activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    ContextActivityKeeper.activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    ContextActivityKeeper.activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    ContextActivityKeeper.activity = null
  }
}
