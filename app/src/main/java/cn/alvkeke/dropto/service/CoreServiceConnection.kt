package cn.alvkeke.dropto.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import cn.alvkeke.dropto.service.CoreService.CoreSrvBinder

open class CoreServiceConnection() : ServiceConnection {

    lateinit var binder: CoreSrvBinder
        private set
    lateinit var service: CoreService
        private set

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as CoreSrvBinder
        service = binder.service
        execOnServiceConnected(componentName)
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        execOnServiceDisconnected(componentName)
    }

    open fun execOnServiceConnected(componentName: ComponentName) {}

    open fun execOnServiceDisconnected(componentName: ComponentName) {}
}
