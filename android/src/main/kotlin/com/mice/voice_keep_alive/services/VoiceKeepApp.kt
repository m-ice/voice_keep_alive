package com.mice.voice_keep_alive.services

import android.app.Activity
import android.app.Application
import android.os.Bundle

class VoiceKeepApp : Application() {

    companion object {
        @Volatile
        var isForeground = false
    }

    private var resumedCount = 0

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {

            override fun onActivityResumed(activity: Activity) {
                resumedCount++
                isForeground = true
            }

            override fun onActivityPaused(activity: Activity) {
                resumedCount--
                if (resumedCount <= 0) {
                    isForeground = false
                }
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
