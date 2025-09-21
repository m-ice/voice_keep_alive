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
class VoiceKeepAlivePlugin: FlutterPlugin, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private var context: Context? = null
  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext;
    ContextActivityKeeper.context=context
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "voice_keep_alive")
    channel.setMethodCallHandler(MethodChannelImpl())
    AppUtil.appHandler = Handler(Looper.getMainLooper())
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)

    context = null;
    ContextActivityKeeper.context=null
    ContextActivityKeeper.activity=null
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    ContextActivityKeeper.activity=binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    TODO("Not yet implemented")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    TODO("Not yet implemented")
  }

  override fun onDetachedFromActivity() {
    TODO("Not yet implemented")
  }
}
