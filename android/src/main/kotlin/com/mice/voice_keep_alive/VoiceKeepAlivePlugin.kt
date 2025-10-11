package com.mice.voice_keep_alive

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.mice.voice_keep_alive.channels.MethodChannelImpl
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
    channel.setMethodCallHandler(MethodChannelImpl())
    AppUtil.appHandler = Handler(Looper.getMainLooper())
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    context = null
    // 安全清理 ContextActivityKeeper 中的引用
    ContextActivityKeeper.clear()
  }

  /** 当插件绑定到 Activity 时触发 */
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    ContextActivityKeeper.activity = binding.activity
  }

  /** Activity 因配置变更分离 */
  override fun onDetachedFromActivityForConfigChanges() {
    ContextActivityKeeper.activity = null
  }

  /** 配置变更后重新绑定 Activity */
  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    ContextActivityKeeper.activity = binding.activity
  }

  /** Activity 完全解绑时 */
  override fun onDetachedFromActivity() {
    ContextActivityKeeper.activity = null
  }
}
