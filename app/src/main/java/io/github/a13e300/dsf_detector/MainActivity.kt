package io.github.a13e300.dsf_detector

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.SurfaceControlHidden
import android.view.View
import android.view.ViewGroup
import android.view.ViewHidden
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.ScreenCapture
import org.lsposed.hiddenapibypass.HiddenApiBypass

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
    companion object {
        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.setHiddenApiExemptions("")
            }
        }
    }
    private lateinit var statusView: TextView
    private var result: DetectResult = DetectResult.DETECTING
    private lateinit var textView: TextView

    private lateinit var handler: Handler
    private lateinit var wm: WindowManager
    private lateinit var viewOfVd: View
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
        textView = TextView(this)
        textView.setOnClickListener {
            copyText()
        }
        v.addView(ScrollView(this).apply {
            addView(textView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )) },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        v.addView(statusView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        title = "${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}"
        logd("Disable Flag Secure Detector ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        logd("System Information: ${Build.VERSION.RELEASE} SDK=${Build.VERSION.SDK_INT}")
        setContentView(v)
        handler = Handler(Looper.myLooper()!!)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // doDetectWindowFlags()
    }

    private fun copyText() {
        val t = textView.text
        if (t.isEmpty()) return
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("", t))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // textView.append("focus changed $hasFocus\n")
        if (hasFocus && result == DetectResult.DETECTING)
            doDetectCaptureSelf()
    }

    private fun doDetectCaptureSelf() {
        kotlin.runCatching {
            val sc = (window.decorView as ViewHidden).viewRootImpl.surfaceControl
            when (Build.VERSION.SDK_INT) {
                in Build.VERSION_CODES.UPSIDE_DOWN_CAKE..Int.MAX_VALUE -> {
                    val buf = ScreenCapture.captureLayers(
                        ScreenCapture.LayerCaptureArgs.Builder(sc)
                            .setUid(Process.myUid().toLong())
                            .build()
                    )
                    if (buf != null) {
                        logd("doDetectCaptureSelf: found?")
                        /*
                        val f = File(filesDir, "a.png")
                        f.outputStream().use {
                            buf.asBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        logd("saved to $f")*/
                        // updateStatus(DetectResult.FOUND)
                    }
                }

                in Build.VERSION_CODES.S..Build.VERSION_CODES.TIRAMISU -> {
                    if (SurfaceControlHidden.captureLayers(
                            SurfaceControlHidden.LayerCaptureArgs.Builder(sc)
                                .setUid(Process.myUid().toLong())
                                .build()
                        ) != null
                    ) {
                        loge("doDetectCaptureSelf: found?")
                        // updateStatus(DetectResult.FOUND)
                    }
                }

                else -> {
                    // not available on A11 and below
                    logd("doDetectCaptureSelf: unsupported")
                }
            }
        }.onFailure {
            loge("doDetectCaptureSelf: ", it)
            updateStatus(DetectResult.ERROR)
        }
        if (result != DetectResult.FOUND) {
            doDetectVirtualDisplay()
        }
    }

    // https://github.com/DerpFest-AOSP/frameworks_base/blob/dc45b5f510c6bf1b64186211cf1c7d41c3003c58/core/java/android/view/Window.java#L1295
    private fun doDetectWindowFlags() {
        val attr = window.attributes
        if (attr.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) {
            logd("doDetectWindowFlags: Found futile DFS!")
            updateStatus(DetectResult.FOUND)
        } else {
            logd("doDetectWindowFlags: not found")
        }
    }

    enum class DetectResult {
        DETECTING,
        FOUND,
        NOT_FOUND,
        ERROR,
        TIMEOUT
    }

    private fun updateStatus(detectResult: DetectResult) {
        result = detectResult
        when (detectResult) {
            DetectResult.DETECTING -> {
                statusView.setText(R.string.detecting)
            }
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
        if (state > UNINITIALIZED) {
            state = FINISHED
            release()
        }
    }

    private val checkTimeoutRunnable = Runnable {
        if (state >= FINISHED) return@Runnable
        logd("check timeout: current state $state")
        onImageAvailable("last")
        if (state < FRAME_DELIVERED) updateStatus(DetectResult.TIMEOUT)
        else if (state < FINISHED) updateStatus(DetectResult.NOT_FOUND)
    }

    // https://wossoneri.github.io/2018/04/02/[Android]MediaProjection-Screenshot/

    private fun doDetectVirtualDisplay() = kotlin.runCatching {
        val displayManager = getSystemService(DisplayManager::class.java)
        reader = ImageReader.newInstance(10, 10, PixelFormat.RGBA_8888, 1)
        state = READER_CREATED
        virtualDisplay = displayManager.createVirtualDisplay(
            "detect", 10, 10, 1, reader.surface, 0
        )
        state = DISPLAY_CREATED
        val ctx = createDisplayContext(virtualDisplay.display)
        wm = ctx.getSystemService(WindowManager::class.java)
        viewOfVd = object : View(ctx) {
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
        wm.addView(viewOfVd, WindowManager.LayoutParams().apply {
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
        updateStatus(DetectResult.ERROR)
        release()
    }

    private fun onImageAvailable(reason: String) {
        if (state >= FINISHED) return
        logd("onImageAvailable: $reason")
        kotlin.runCatching { reader.acquireLatestImage() }
            .onFailure { loge("onImageAvailable: failed to get frame (current frame $frameCount)", it) }
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
                        loge("onImageAvailable: detected at frame $frameCount ($reason)")
                        updateStatus(DetectResult.FOUND)
                    } else {
                        state = FRAME_DELIVERED
                        logd("onImageAvailable: not detected at frame $frameCount ($reason)")
                    }
                }.onFailure {
                    updateStatus(DetectResult.ERROR)
                }
                return
            }
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        if (state == UNINITIALIZED) {
            return
        }
        if (state < RELEASED) {
            handler.removeCallbacks(checkTimeoutRunnable)
            runCatching {
                if (state >= VIEW_ADDED)
                    wm.removeViewImmediate(viewOfVd)
                if (state >= DISPLAY_CREATED)
                    virtualDisplay.release()
                if (state >= READER_CREATED)
                    reader.close()
            }.onFailure {
                loge("release failed", it)
            }.onSuccess {
                logd("release success")
            }
            state = RELEASED

        }
    }

    private fun logd(info: String) {
        Log.d(TAG, info)
        runOnUiThread {
            textView.append(info)
            textView.append("\n")
        }
    }

    private fun loge(info: String, t: Throwable? = null) {
        if (t == null) Log.e(TAG, info)
        else Log.e(TAG, info, t)
        runOnUiThread {
            textView.append(SpannableStringBuilder()
                .append(info, ForegroundColorSpan(Color.RED), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                .append("\n")
                .apply { if (t != null) {
                    append(Log.getStackTraceString(t), ForegroundColorSpan(Color.RED), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    append("\n")
                } }
            )
        }
    }
}