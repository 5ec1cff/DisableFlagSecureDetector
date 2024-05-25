package io.github.a13e300.dsf_detector

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var handler: Handler
    private lateinit var statusView: TextView
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
        val thread = HandlerThread("detect")
        thread.start()
        handler = Handler(thread.looper)
        handler.post(this::doDetect)
    }

    enum class Status {
        FOUND,
        NOT_FOUND,
        ERROR
    }

    private fun reportStatus(status: Status) {
        runOnUiThread {
            when (status) {
                Status.FOUND -> {
                    statusView.setText(R.string.found)
                    statusView.setTextColor(getColor(R.color.found_color))
                }
                Status.NOT_FOUND -> {
                    statusView.setText(R.string.not_found)
                    statusView.setTextColor(getColor(R.color.not_found_color))
                }
                Status.ERROR -> {
                    statusView.setText(R.string.error)
                    statusView.setTextColor(getColor(R.color.error_color))
                }
            }
        }
    }

    // https://wossoneri.github.io/2018/04/02/[Android]MediaProjection-Screenshot/

    private fun doDetect() = kotlin.runCatching {
        val displayManager = getSystemService(DisplayManager::class.java)
        val reader = ImageReader.newInstance(10, 10, PixelFormat.RGBA_8888, 1)
        val virtualDisplay = displayManager.createVirtualDisplay(
            "detect", 10, 10, 1, reader.surface, 0
        )
        val ctx = createDisplayContext(virtualDisplay.display)
        val wm = ctx.getSystemService(WindowManager::class.java)
        val v = View(ctx).apply {
            setBackgroundColor(Color.RED)
        }
        reader.setOnImageAvailableListener({
            val img = kotlin.runCatching { it.acquireLatestImage() }.getOrNull()
            if (img == null) {
                reportStatus(Status.ERROR)
                return@setOnImageAvailableListener
            }
            runCatching {
                val planes = img.planes[0]
                val bytes = planes.buffer
                val pixelStride = planes.pixelStride
                val rowStride = planes.rowStride
                val rowPadding = rowStride - pixelStride * 10
                val w = 10 + rowPadding / pixelStride
                val bitmap = Bitmap.createBitmap(w, 10, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(bytes)

                if (bitmap.getPixel(0, 0) == Color.RED) {
                    reportStatus(Status.FOUND)
                } else {
                    reportStatus(Status.NOT_FOUND)
                }
                handler.post {
                    runCatching {
                        wm.removeView(v)
                        virtualDisplay.release()
                    }
                }
            }.onFailure {
                reportStatus(Status.ERROR)
            }
            runCatching {
                img.close()
            }
        }, null)
        wm.addView(v, WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION
            width = 10
            height = 10
            x = 0
            y = 0
            format = PixelFormat.RGBA_8888
            title = "view1"
            flags = WindowManager.LayoutParams.FLAG_SECURE
        })
    }.onFailure {
        reportStatus(Status.ERROR)
    }
}