package cn.alvkeke.dropto.ui.activity

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.mgmt.Global
import cn.alvkeke.dropto.storage.DataBaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            DataBaseHelper(this@StartupActivity).use { helper ->
                helper.start()
                // do nothing, just to trigger database creation / upgrade / downgrade
                helper.finish()
            }
            Global.folderInitAndMigrate(this@StartupActivity)

            withContext(Dispatchers.Main) {
                startActivity(Intent(this@StartupActivity, MainActivity::class.java))
                finish()
            }
        }
    }
}