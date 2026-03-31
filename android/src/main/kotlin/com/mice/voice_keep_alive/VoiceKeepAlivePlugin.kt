package com.mice.voice_keep_alive

import android.content.Context
import android.content.Intent
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

    companion object {
        var channel: MethodChannel? = null
        var cachedIntent: Intent? = null

        fun handleIntent(intent: Intent?) {
            val roomParams = intent?.getStringExtra("roomParams") ?: return
            channel?.invokeMethod("openRoom", roomParams) ?: run {
                cachedIntent = intent
            }
        }

        fun handleCachedIntent() {
            cachedIntent?.let {
                handleIntent(it)
                cachedIntent = null
            }
        }
    }

    private var context: Context? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        ContextActivityKeeper.context = context
        channel = MethodChannel(binding.binaryMessenger, "voice_keep_alive")
        channel?.setMethodCallHandler(MethodChannelImpl())
        AppUtil.appHandler = Handler(Looper.getMainLooper())
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        channel = null
        context = null
        cachedIntent = null
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