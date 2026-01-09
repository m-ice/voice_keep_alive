package com.mice.voice_keep_alive.utils

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

/**
 * 安全的 Context 与 Activity 保存工具
 * 任何地方访问都不会抛异常
 */
public object ContextActivityKeeper {
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

    var lastActive: Boolean = false
    /**
     * 清理引用，建议在插件 onDetachedFromEngine 时调用
     */
    fun clear() {
        contextWeakReference?.clear()
        activityWeakReference?.clear()
        contextWeakReference = null
        activityWeakReference = null
    }
}
