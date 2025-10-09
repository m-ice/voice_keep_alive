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

/** VoiceKeepAlivePlugin */
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
    ContextActivityKeeper.context = null
    ContextActivityKeeper.activity = null
  }

  /** 当插件绑定到 Activity 时触发 */
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    ContextActivityKeeper.activity = binding.activity
  }

  /** 当 Activity 因配置变更（如横竖屏切换）分离时调用 */
  override fun onDetachedFromActivityForConfigChanges() {
    // 清除当前 Activity 引用，防止泄漏
    ContextActivityKeeper.activity = null
  }

  /** 当配置变更后新的 Activity 重新附加时调用 */
  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    // 重新保存 Activity 引用
    ContextActivityKeeper.activity = binding.activity
  }

  /** 当插件与 Activity 完全解绑时（Activity 被销毁）调用 */
  override fun onDetachedFromActivity() {
    // 清除 Activity 引用
    ContextActivityKeeper.activity = null
  }
}
