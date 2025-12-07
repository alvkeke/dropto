package cn.alvkeke.dropto.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import cn.alvkeke.dropto.service.CoreService.CoreSrvBinder
import cn.alvkeke.dropto.service.Task.ResultListener

open class CoreServiceConnection(private val listener: ResultListener?) : ServiceConnection {
    @get:Suppress("unused")
    var binder: CoreSrvBinder? = null
        private set
    var service: CoreService? = null
        private set
    private var bundleAfterConnected: Bundle? = null

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as CoreSrvBinder
        service = binder!!.service
        if (listener != null) service!!.addTaskListener(listener)
        execOnServiceConnected(componentName, bundleAfterConnected)
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        if (binder == null) return
        if (listener != null) service!!.delTaskListener(listener)
        execOnServiceDisconnected()
        service = null
        binder = null
    }

    fun setBundleAfterConnected(bundleAfterConnected: Bundle?) {
        this.bundleAfterConnected = bundleAfterConnected
    }

    open fun execOnServiceConnected(componentName: ComponentName, bundleAfterConnected: Bundle?) {}

    open fun execOnServiceDisconnected() {}
}
