package cn.alvkeke.dropto

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import cn.alvkeke.dropto.service.CoreService
import cn.alvkeke.dropto.service.CoreServiceConnection
import cn.alvkeke.dropto.storage.DataManager
import cn.alvkeke.dropto.storage.ImageLoader

class DroptoApplication : Application() {

    private var isServiceBound = false

    private val serviceConn: CoreServiceConnection = object : CoreServiceConnection() {
        override fun execOnServiceConnected(componentName: ComponentName) {
            DataManager.coreService = service
            isServiceBound = true
            Log.d(TAG, "CoreService connected in Application")
        }

        override fun execOnServiceDisconnected(componentName: ComponentName) {
            DataManager.coreService = null
            isServiceBound = false
            Log.d(TAG, "CoreService disconnected in Application")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DroptoApplication onCreate")
        DataManager.init(this)
        // FIXME: workaround: set to dark mode always now, need to support light mode later
//        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        ImageLoader.initImageLoader(this)
    }

    // disable the service for now since all data access is moved to DataManager
    // TODO: re-enable the service when we need to support network sync
    @Suppress("unused")
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

    companion object {
        private const val TAG = "DroptoApplication"
    }
}
