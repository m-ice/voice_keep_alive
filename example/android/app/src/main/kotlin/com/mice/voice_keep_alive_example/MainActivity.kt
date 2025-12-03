package com.mice.voice_keep_alive_example

import android.content.Intent
import android.os.Bundle
import com.mice.voice_keep_alive.VoiceKeepAlivePlugin
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VoiceKeepAlivePlugin.handleCachedIntent()
        VoiceKeepAlivePlugin.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        VoiceKeepAlivePlugin.handleCachedIntent()
        VoiceKeepAlivePlugin.handleIntent(intent)
    }
}
