package io.github.a13e300.dsf_detector

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.SurfaceControlHidden
import android.view.ViewGroup
import android.view.ViewHidden
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.ScreenCapture
import org.lsposed.hiddenapibypass.HiddenApiBypass

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
        setContentView(v)
        doDetectWindowFlags()
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
                    if (ScreenCapture.captureLayers(
                            ScreenCapture.LayerCaptureArgs.Builder(sc)
                                .setUid(Process.myUid().toLong())
                                .build()
                        ) != null
                    ) {
                        updateStatus(DetectResult.FOUND)
                    } else {
                        updateStatus(DetectResult.NOT_FOUND)
                    }

                }

                in Build.VERSION_CODES.S..Build.VERSION_CODES.TIRAMISU -> {
                    if (SurfaceControlHidden.captureLayers(
                            SurfaceControlHidden.LayerCaptureArgs.Builder(sc)
                                .setUid(Process.myUid().toLong())
                                .build()
                        ) != null
                    ) {
                        updateStatus(DetectResult.FOUND)
                    } else {
                        updateStatus(DetectResult.NOT_FOUND)
                    }
                }

                else -> {
                    // not available on A11 and below
                    textView.append("doDetectCaptureSelf: unsupported")
                    updateStatus(DetectResult.NOT_FOUND)
                }
            }
        }.onFailure {
            Log.e(TAG, "doDetectCaptureSelf: ", it)
            updateStatus(DetectResult.ERROR)
            textView.text = Log.getStackTraceString(it)
        }
    }

    // https://github.com/DerpFest-AOSP/frameworks_base/blob/dc45b5f510c6bf1b64186211cf1c7d41c3003c58/core/java/android/view/Window.java#L1295
    private fun doDetectWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val attr = window.attributes
        if (attr.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) {
            Log.d(TAG, "doDetectWindowFlags: found custom rom")
            updateStatus(DetectResult.FOUND)
            textView.append("Found futile DFS!\n")
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
    }

}