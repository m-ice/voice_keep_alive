package com.mice.voice_keep_alive.utils

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference


object ContextActivityKeeper {
    private var contextWeakReference: WeakReference<Context>? = null
    private var activityWeakReference: WeakReference<Activity>? = null
    var context: Context?
        get() {
            if (contextWeakReference == null) {
                throw NullPointerException("contextWeakReference is null")
            }
            val context: Context = contextWeakReference!!.get()!!
            return context
        }
        set(context) {
            contextWeakReference = if (context == null) {
                null
            } else {
                WeakReference<Context>(context)
            }
        }

    var activity: Activity?
        get() {
            if (activityWeakReference == null) {
                throw NullPointerException("contextWeakReference is null")
            }
            val activity: Activity = activityWeakReference!!.get()!!
            return activity
        }
        set(activity) {
            activityWeakReference = if (activity == null) {
                null
            } else {
                WeakReference<Activity>(activity)
            }
        }
}
