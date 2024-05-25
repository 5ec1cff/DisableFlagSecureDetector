package io.github.a13e300.dsf_detector

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

const val UNINITIALIZED = 0
const val READER_CREATED = 1
const val DISPLAY_CREATED = 2
const val VIEW_ADDED = 3
const val VIEW_DRAWN = 4
const val FRAME_DELIVERED = 5
const val FINISHED = 6
const val RELEASED = 7

const val TAG = "DisableFlagSecureDetector"

class MainActivity : Activity() {
    private lateinit var handler: Handler
    private lateinit var statusView: TextView
    private lateinit var wm: WindowManager
    private lateinit var v: View
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var reader: ImageReader
    private var state: Int = UNINITIALIZED
    private var frameCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = FrameLayout(this)
        statusView = TextView(this)
        statusView.setText(R.string.detecting)
        statusView.textSize = resources.getDimension(R.dimen.font_size)
        v.addView(statusView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
            )
        )
        setContentView(v)
        handler = Handler(Looper.myLooper()!!)
        doDetect()
    }

    enum class DetectResult {
        FOUND,
        NOT_FOUND,
        ERROR,
        TIMEOUT
    }

    private fun reportStatus(detectResult: DetectResult) {
        when (detectResult) {
            DetectResult.FOUND -> {
                statusView.setText(R.string.found)
                statusView.setTextColor(getColor(R.color.found_color))
            }
            DetectResult.NOT_FOUND -> {
                statusView.setText(R.string.not_found)
                statusView.setTextColor(getColor(R.color.not_found_color))
            }
            DetectResult.ERROR -> {
                statusView.setText(R.string.error)
                statusView.setTextColor(getColor(R.color.error_color))
            }
            DetectResult.TIMEOUT -> {
                statusView.setText(R.string.timeout)
                statusView.setTextColor(getColor(R.color.error_color))
            }
        }
        state = FINISHED
        release()
    }

    private val checkTimeoutRunnable = Runnable {
        if (state >= FINISHED) return@Runnable
        Log.d(TAG, "check timeout: current state $state")
        onImageAvailable("last")
        if (state < FRAME_DELIVERED) reportStatus(DetectResult.TIMEOUT)
        else if (state < FINISHED) reportStatus(DetectResult.NOT_FOUND)
    }

    // https://wossoneri.github.io/2018/04/02/[Android]MediaProjection-Screenshot/

    private fun doDetect() = kotlin.runCatching {
        val displayManager = getSystemService(DisplayManager::class.java)
        reader = ImageReader.newInstance(10, 10, PixelFormat.RGBA_8888, 1)
        state = READER_CREATED
        virtualDisplay = displayManager.createVirtualDisplay(
            "detect", 10, 10, 1, reader.surface, 0
        )
        state = DISPLAY_CREATED
        val ctx = createDisplayContext(virtualDisplay.display)
        wm = ctx.getSystemService(WindowManager::class.java)
        v = object : View(ctx) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (state != VIEW_ADDED) return
                state = VIEW_DRAWN
                handler.removeCallbacks(checkTimeoutRunnable)
                handler.postDelayed(checkTimeoutRunnable, 2000)
                handler.postDelayed({
                    if (state == VIEW_DRAWN) {
                        onImageAvailable("draw")
                    }
                }, 500)
            }
        }.apply {
            setBackgroundColor(Color.RED)
        }
        reader.setOnImageAvailableListener(
            {
                onImageAvailable("ImageReader")
            }, null)
        wm.addView(v, WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION
            width = 10
            height = 10
            x = 0
            y = 0
            format = PixelFormat.RGBA_8888
            title = "detect"
            flags = WindowManager.LayoutParams.FLAG_SECURE
        })
        state = VIEW_ADDED
        handler.postDelayed(checkTimeoutRunnable, 2000)
    }.onFailure {
        reportStatus(DetectResult.ERROR)
        release()
    }

    private fun onImageAvailable(reason: String) {
        if (state >= FINISHED) return
        Log.d(TAG, "onImageAvailable: $reason")
        kotlin.runCatching { reader.acquireLatestImage() }
            .onFailure { Log.e(TAG, "onImageAvailable: failed to get frame (current frame $frameCount)", it) }
            .getOrNull()
            ?.use { img ->
                runCatching {
                    val planes = img.planes[0]
                    val bytes = planes.buffer
                    val pixelStride = planes.pixelStride
                    val rowStride = planes.rowStride
                    val rowPadding = rowStride - pixelStride * 10
                    val w = 10 + rowPadding / pixelStride
                    val bitmap = Bitmap.createBitmap(w, 10, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(bytes)
                    frameCount += 1
                    if (bitmap.getPixel(0, 0) == Color.RED) {
                        Log.d(TAG, "onImageAvailable: detected at frame $frameCount ($reason)")
                        reportStatus(DetectResult.FOUND)
                    } else {
                        state = FRAME_DELIVERED
                        Log.d(TAG, "onImageAvailable: not detected at frame $frameCount ($reason)")
                    }
                }.onFailure {
                    reportStatus(DetectResult.ERROR)
                }
                return
            }
    }

    override fun onDestroy() {
        state = FINISHED
        release()
        super.onDestroy()
    }

    private fun release() {
        if (state < RELEASED) {
            handler.removeCallbacks(checkTimeoutRunnable)
            runCatching {
                if (state >= VIEW_ADDED)
                    wm.removeViewImmediate(v)
                if (state >= DISPLAY_CREATED)
                    virtualDisplay.release()
                if (state >= READER_CREATED)
                    reader.close()
            }.onFailure {
                Log.e(TAG, "release failed", it)
            }.onSuccess {
                Log.d(TAG, "release success")
            }
            state = RELEASED

        }
    }
}