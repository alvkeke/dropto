package cn.alvkeke.dropto.ui.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.storage.ImageLoader
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener
import cn.alvkeke.dropto.ui.listener.GestureListener
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ImageViewerFragment : DialogFragment(), FragmentOnBackListener {
    private lateinit var parentView: View
    private lateinit var imageView: ImageView
    private var imgFile: File? = null
    private var loadedBitmap: Bitmap? = null

    fun setImgFile(imgFile: File) {
        this.imgFile = imgFile
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        parentView = inflater.inflate(R.layout.fragment_image_viewer, container, false)
        return parentView
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (imgFile == null || !imgFile!!.exists() || !imgFile!!.isFile) {
            Toast.makeText(requireContext(), "no Image view, exit", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        view.setBackgroundColor(Color.BLACK)
        imageView = view.findViewById(R.id.img_viewer_image)
        ImageLoader.loadOriginalImageAsync(imgFile!!) { bitmap ->
            loadedBitmap = bitmap
            imageView.setImageBitmap(bitmap)
        }
        view.setOnTouchListener(ImageGestureListener())
    }

    private var window: Window? = null
    private var isFull = true
    private fun toggleFullScreen() {
        if (isFull) {
            window!!.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        } else {
            window!!.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        isFull = !isFull
    }

    override fun onResume() {
        super.onResume()
        window = requireDialog().window
        if (window == null) return
        window!!.statusBarColor = Color.TRANSPARENT
        window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        toggleFullScreen()
    }

    override fun onBackPressed(): Boolean {
        finish()
        return true
    }

    private inner class ImageGestureListener : GestureListener() {
        override fun onClick(v: View, e: MotionEvent) {
            toggleFullScreen()
        }

        override fun onDoubleClick(v: View, e: MotionEvent) {
            if (scaleFactor > 1) {
                animeScaleImageTo(1f)
                animeTranslateImageTo(0f, 0f)
            } else {
                animeScaleImageTo(2f)
            }
        }

        val exitThreshold: Float
            get() = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 100f, resources.displayMetrics
            )

        override fun onScrollVertical(view: View, deltaY: Float): Boolean {
            if (scaleFactor != 1f) return false
            val length = imageView.height.toFloat() / 3
            val y = abs(imageView.translationY)
            val current = min(y, length)
            var ratio = 1 - (current / length)
            ratio = max(ratio, 0f) // limit background transparent

            parentView.background.alpha = (ratio * 0xff).toInt()
            imageView.translationY += deltaY
            return true
        }

        override fun onScrollVerticalEnd(view: View, motionEvent: MotionEvent): Boolean {
            if (scaleFactor != 1f) return false
            if (abs(imageView.translationY) > this.exitThreshold) {
                finish()
            } else {
                val dy = imageView.translationY
                val animator = ValueAnimator.ofFloat(dy, 0f)
                animator.addUpdateListener { valueAnimator: ValueAnimator ->
                    imageView.translationY = (valueAnimator.animatedValue as Float)
                }
                animator.start()
                parentView.background.alpha = 0xff
            }
            return true
        }

        override fun onScrollHorizontal(view: View, deltaX: Float): Boolean {
            return scaleFactor == 1f
        }

        override fun onScrollHorizontalEnd(view: View, motionEvent: MotionEvent): Boolean {
            return scaleFactor == 1f
        }

        override fun onDrag(view: View, deltaX: Float, deltaY: Float) {
            if (scaleFactor == 1f) return
            translateImage(deltaX, deltaY)
        }

        private val pointF = PointF()
        override fun onDragEnd(view: View, motionEvent: MotionEvent) {
            if (scaleFactor == 1f) return
            adjustImagePosition(pointF)
            animeTranslateImageTo(pointF.x, pointF.y)
        }

        override fun onZoom(view: View, ratio: Float) {
            scaleImage(ratio)
        }

        override fun onZoomEnd(view: View) {
            if (scaleFactor < 1) {
                animeScaleImageTo(1f)
                animeTranslateImageTo(0f, 0f)
            } else {
                adjustImagePosition(pointF)
                animeTranslateImageTo(pointF.x, pointF.y)
            }
        }
    }

    private var imageFixHeight = -1f
    private var imageFixWidth = -1f
    private fun calcImageFixSize() {
        val ratio1 = imageView.height.toFloat() / imageView.width
        val ratio2 = loadedBitmap!!.height.toFloat() / loadedBitmap!!.width
        if (ratio1 > ratio2) {
            imageFixWidth = imageView.width.toFloat()
            imageFixHeight = imageFixWidth * ratio2
        } else {
            imageFixHeight = imageView.height.toFloat()
            imageFixWidth = imageFixHeight / ratio2
        }
    }

    fun getImageFixHeight(): Float {
        if (imageFixHeight == -1f) calcImageFixSize()
        return imageFixHeight
    }

    fun getImageFixWidth(): Float {
        if (imageFixWidth == -1f) calcImageFixSize()
        return imageFixWidth
    }

    private val imageCenterX: Float
        get() = imageView.translationX + imageView.width.toFloat() / 2
    private val imageCenterY: Float
        get() = imageView.translationY + imageView.height.toFloat() / 2

    private fun getVisibleRect(rect: RectF) {
        val centerX = this.imageCenterX
        val centerY = this.imageCenterY
        val widthHalf = getImageFixWidth() * scaleFactor / 2
        val heightHalf = getImageFixHeight() * scaleFactor / 2
        val left = centerX - widthHalf
        val right = centerX + widthHalf
        val top = centerY - heightHalf
        val bottom = centerY + heightHalf
        rect.set(left, top, right, bottom)
    }

    private fun centerToTranslation(point: PointF) {
        point.x -= imageView.width.toFloat() / 2
        point.y -= imageView.height.toFloat() / 2
    }

    private val rect = RectF()
    private fun adjustImagePosition(point: PointF) {
        val maxRight = parentView.width.toFloat()
        val maxBottom = parentView.height.toFloat()
        getVisibleRect(rect)

        var diff: Float
        var length: Float
        if ((rect.width().also { length = it }) < maxRight) {
            diff = maxRight - length
            diff /= 2f
            rect.offsetTo(diff, rect.top)
        } else {
            if (rect.left > 0) rect.offset(-rect.left, 0f)
            if (((maxRight - rect.right).also { diff = it }) > 0) rect.offset(diff, 0f)
        }

        if ((rect.height().also { length = it }) < maxBottom) {
            diff = maxBottom - length
            diff /= 2f
            rect.offsetTo(rect.left, diff)
        } else {
            if (rect.top > 0) rect.offset(0f, -rect.top)
            if (((maxBottom - rect.bottom).also { diff = it }) > 0) rect.offset(0f, diff)
        }

        point.set(rect.centerX(), rect.centerY())
        centerToTranslation(point)
    }

    private var scaleFactor = 1f
    private fun scaleImage() {
        imageView.scaleX = scaleFactor
        imageView.scaleY = scaleFactor
    }

    private fun scaleImage(scale: Float) {
        scaleFactor *= scale
        if (scaleFactor > 10) scaleFactor = 10f
        if (scaleFactor < 0.1) scaleFactor = 0.1f
        scaleImage()
    }

    private fun scaleImageTo(targetScale: Float) {
        scaleFactor = targetScale
        scaleImage()
    }

    private fun animeScaleImageTo(targetScale: Float) {
        val animator = ValueAnimator.ofFloat(scaleFactor, targetScale)
        animator.addUpdateListener { valueAnimator: ValueAnimator ->
            scaleImageTo((valueAnimator.animatedValue as Float))
        }
        animator.start()
    }

    private fun translateImage(x: Float, y: Float) {
        translateImageTo(
            x + imageView.translationX,
            y + imageView.translationY
        )
    }

    private fun translateImageTo(targetX: Float, targetY: Float) {
        imageView.translationX = targetX
        imageView.translationY = targetY
    }

    private fun animeTranslateImageTo(targetX: Float, targetY: Float) {
        val startX = imageView.translationX
        val startY = imageView.translationY
        val diffX = targetX - startX
        val diffY = targetY - startY
        if (diffY == 0f && diffX == 0f) return
        val useY: Boolean
        val start: Float
        val end: Float
        if (diffY != 0f) {
            useY = true
            start = startY
            end = targetY
        } else {
            useY = false
            start = startX
            end = targetX
        }
        val animator = ValueAnimator.ofFloat(start, end)
        animator.addUpdateListener { valueAnimator: ValueAnimator ->
            val x: Float
            val y: Float
            if (useY) {
                y = valueAnimator.animatedValue as Float
                x = startX + diffX * valueAnimator.animatedFraction
            } else {
                x = valueAnimator.animatedValue as Float
                y = startY + diffY * valueAnimator.animatedFraction
            }
            translateImageTo(x, y)
        }
        animator.start()
    }

    private fun fragmentEnd() {
        dismiss()
    }

    fun finish() {

        val startY = imageView.translationY
        val endY = parentView.height.toFloat()
        val startT = parentView.alpha
        val animator = ValueAnimator.ofFloat(startY, endY)
        animator.addUpdateListener { valueAnimator: ValueAnimator ->
            imageView.translationY = (valueAnimator.animatedValue as Float)
            val progress = valueAnimator.animatedFraction
            parentView.alpha = startT - startT * progress
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                fragmentEnd()
            }
        })
        animator.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (loadedBitmap != null) {
            loadedBitmap!!.recycle()
            loadedBitmap = null
        }
    }
}