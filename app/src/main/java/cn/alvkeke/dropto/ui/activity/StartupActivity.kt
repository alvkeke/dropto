package cn.alvkeke.dropto.ui.activity

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.storage.DataManager
import cn.alvkeke.dropto.storage.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds


const val TAG = "StartupActivity"

class StartupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initView = object : View(this) {

            private val backgroundPaint = Paint().apply {
                color = ContextCompat.getColor(context, R.color.color_background)
            }

            private val ringPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 16f
                isAntiAlias = true
            }
            private val ringRect = RectF()

            private var sweepAngle = 0f
            private val ringRadius = 80f
            private val handler = android.os.Handler(android.os.Looper.getMainLooper())
            private val updateRunnable = object : Runnable {
                override fun run() {
                    sweepAngle += 8f
                    if (sweepAngle >= 360f) sweepAngle -= 360f
                    invalidate()
                    handler.postDelayed(this, 16)
                }
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                handler.post(updateRunnable)
            }

            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                handler.removeCallbacks(updateRunnable)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.drawRect(
                    0f, 0f,
                    width.toFloat(), height.toFloat(),
                    backgroundPaint
                )
                val cx = width / 2f
                val cy = height / 2f
                val radius = ringRadius
                ringRect.set(
                    cx - radius,
                    cy - radius,
                    cx + radius,
                    cy + radius
                )
                canvas.drawArc(
                    ringRect,
                    sweepAngle,
                    270f,
                    false,
                    ringPaint
                )
            }
        }
        setContentView(initView)

        lifecycleScope.launch(Dispatchers.IO) {
            FileHelper.folderInitAndMigrate(this@StartupActivity)
            DataManager.migrateLegacyDatabase()
            val success = DataManager.loadActiveUser()

            if (success) {
                postLoginExec()
            } else {
                showLogin {
                    postLoginExec()
                }
            }
        }
    }

    private suspend fun showLogin(done: suspend () -> Unit) = withContext(Dispatchers.Main) {
        // TODO: invoke Login Fragment here, for now, active local user

        Toast.makeText(this@StartupActivity,
            "Activating Local User",
            Toast.LENGTH_SHORT).show()

        val ret = withContext(Dispatchers.IO) {
            delay(3000.milliseconds)
            if (!DataManager.activeLocalUser()) {
                Log.e(TAG, "Failed to Active Local User")
                return@withContext false
            }
            if (!DataManager.loadActiveUser()) {
                Log.e(TAG, "Failed to Load Active User")
                return@withContext false
            }
            true
        }

        if (!ret) {
            Toast.makeText(this@StartupActivity,
                "Failed to Active Local User/Load Local User",
                Toast.LENGTH_SHORT).show()
            finish()
            return@withContext
        }

        done()
    }

    /**
     * exec Post login
     * - prepareDatabase()
     * - jump to MainActivity
     */
    private suspend fun postLoginExec() {
        withContext(Dispatchers.IO) {
            DataManager.prepareDatabase()
        }
        withContext(Dispatchers.Main) {
            startActivity(Intent(this@StartupActivity, MainActivity::class.java))
            finish()
        }
    }

}