package cn.alvkeke.dropto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Foreground service reserved for the network side.
 *
 * It holds no data logic: every data operation is served by
 * [cn.alvkeke.dropto.runtime.DataManager], which keeps a reference to this service and will drive
 * the sync from there.
 */
class CoreService : Service() {
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private val binder = CoreSrvBinder()

    inner class CoreSrvBinder : Binder() {
        val service: CoreService
            get() = this@CoreService
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_STR_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Notification channel for foreground service of CoreService.\n" +
                "Can be disabled to prevent annoying notification"
        val notificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_STR_ID).build()
    }

    private fun startForeground() {
        val notification = createNotification()
        try {
            startForeground(CHANNEL_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            Log.d(TAG, "startForeground finished")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground: $e")
            Toast.makeText(
                this, "Failed to start coreService",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CoreService onCreate")
        startForeground()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CoreService onDestroy")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val TAG = "CoreService"
        private const val CHANNEL_ID = 100
        private const val CHANNEL_STR_ID = "CHANNEL_ID_CORE_DROP_TO"
        private const val CHANNEL_NAME = "CoreService"
    }
}
