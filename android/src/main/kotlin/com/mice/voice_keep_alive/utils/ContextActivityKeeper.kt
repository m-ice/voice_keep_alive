package com.mice.voice_keep_alive.utils

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

object ContextActivityKeeper {
    private var contextWeakReference: WeakReference<Context>? = null
    private var activityWeakReference: WeakReference<Activity>? = null

    var context: Context?
        get() = contextWeakReference?.get()
        set(value) {
            contextWeakReference = value?.let { WeakReference(it) }
        }

    var activity: Activity?
        get() = activityWeakReference?.get()
        set(value) {
            activityWeakReference = value?.let { WeakReference(it) }
        }

    fun clear() {
        contextWeakReference?.clear()
        activityWeakReference?.clear()
        contextWeakReference = null
        activityWeakReference = null
    }
}