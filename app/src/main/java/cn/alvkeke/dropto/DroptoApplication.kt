package cn.alvkeke.dropto

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import cn.alvkeke.dropto.service.CoreService
import cn.alvkeke.dropto.service.CoreServiceConnection
import cn.alvkeke.dropto.service.Task
import cn.alvkeke.dropto.service.Task.ResultListener

class DroptoApplication : Application(), ResultListener{


    /**
     * Public read-only access to CoreService
     * Use: app.coreService?.queueTask(...)
     */
    val service: CoreService?
        get() {
            if (_coreService == null) {
                Log.w(TAG, "CoreService is not ready yet")
            }
            return _coreService
        }

    private var _coreService: CoreService? = null
    private var isServiceBound = false

    private val serviceConn: CoreServiceConnection = object : CoreServiceConnection() {
        override fun execOnServiceConnected(componentName: ComponentName) {
            this@DroptoApplication._coreService = service
            this@DroptoApplication._coreService!!.resultListener = this@DroptoApplication
            isServiceBound = true
            Log.d(TAG, "CoreService connected in Application")
        }

        override fun execOnServiceDisconnected(componentName: ComponentName) {
            _coreService!!.resultListener = null
            _coreService = null
            isServiceBound = false
            Log.d(TAG, "CoreService disconnected in Application")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DroptoApplication onCreate")
        setupCoreService()
    }

    private fun setupCoreService() {
        val serviceIntent = Intent(this, CoreService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE)
        Log.d(TAG, "CoreService started and bound")
    }

    override fun onTerminate() {
        super.onTerminate()
        if (isServiceBound) {
            unbindService(serviceConn)
        }
        Log.d(TAG, "DroptoApplication onTerminate")
    }

    // to notify all registered ResultListener, move the listener from CoreService to here
    // due to the CoreService may not ready when activity be created
    // and trying to add itself as listener
    private val listeners = ArrayList<ResultListener>()

    override fun onTaskFinish(task: Task) {
        listeners.iterator().forEach { listener ->
            listener.onTaskFinish(task)
        }
    }

    fun addTaskListener(listener: ResultListener) {
        listeners.add(listener)
    }

    fun delTaskListener(listener: ResultListener) {
        listeners.remove(listener)
    }

    companion object {
        private const val TAG = "DroptoApplication"
    }
}

