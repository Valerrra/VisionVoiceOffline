package com.huawei.vision.voiceoffline

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class VoiceOverlayWindowManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceOverlayWindow"
        private const val AUTO_HIDE_DELAY_MS = 3500L
        private const val ERROR_HIDE_DELAY_MS = 3000L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var isViewAttached = false

    private var tvOverlayIcon: TextView? = null
    private var tvOverlayStateBadge: TextView? = null
    private var tvOverlayStt: TextView? = null
    private var layoutOverlayResult: LinearLayout? = null
    private var tvOverlayIntentBadge: TextView? = null
    private var tvOverlayResultText: TextView? = null
    private var tvOverlayMeter: TextView? = null
    private var tvOverlayLatency: TextView? = null

    var currentState: VoiceOverlayState = VoiceOverlayState.IDLE
        private set

    private val autoHideRunnable = Runnable {
        hide()
    }

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    private fun ensureViewInflated(): Boolean {
        if (!canDrawOverlays()) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted; overlay cannot be shown")
            return false
        }

        if (overlayView == null) {
            val inflater = LayoutInflater.from(context)
            @SuppressLint("InflateParams")
            val view = inflater.inflate(R.layout.view_voice_overlay, null)
            overlayView = view

            tvOverlayIcon = view.findViewById(R.id.tvOverlayIcon)
            tvOverlayStateBadge = view.findViewById(R.id.tvOverlayStateBadge)
            tvOverlayStt = view.findViewById(R.id.tvOverlayStt)
            layoutOverlayResult = view.findViewById(R.id.layoutOverlayResult)
            tvOverlayIntentBadge = view.findViewById(R.id.tvOverlayIntentBadge)
            tvOverlayResultText = view.findViewById(R.id.tvOverlayResultText)
            tvOverlayMeter = view.findViewById(R.id.tvOverlayMeter)
            tvOverlayLatency = view.findViewById(R.id.tvOverlayLatency)

            view.visibility = View.GONE
        }
        return true
    }

    private fun attachViewIfNeeded() {
        val view = overlayView ?: return
        if (!isViewAttached) {
            try {
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }

                windowManager.addView(view, params)
                isViewAttached = true
                Log.d(TAG, "Overlay view attached to WindowManager (TYPE_APPLICATION_OVERLAY, non-focusable)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach overlay view to WindowManager", e)
                isViewAttached = false
            }
        }
    }

    fun showListening() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoHideRunnable)
            if (!ensureViewInflated()) return@post

            attachViewIfNeeded()
            currentState = VoiceOverlayState.LISTENING

            tvOverlayIcon?.text = "🎙"
            tvOverlayStateBadge?.text = "СЛУШАЮ"
            tvOverlayStateBadge?.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
            tvOverlayStt?.text = "Говорите в пульт..."
            tvOverlayStt?.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            layoutOverlayResult?.visibility = View.GONE
            tvOverlayMeter?.text = "RMS: -96 dB"
            tvOverlayLatency?.text = "— ms | RTF: —"

            overlayView?.visibility = View.VISIBLE
            Log.d(TAG, "Overlay state: LISTENING")
        }
    }

    fun updatePartial(text: String, rmsDb: Double, peak: Int) {
        mainHandler.post {
            if (currentState != VoiceOverlayState.LISTENING) return@post
            if (text.isNotEmpty()) {
                tvOverlayStt?.text = text
            }
            tvOverlayMeter?.text = "RMS: ${"%.1f".format(rmsDb)} dB"
        }
    }

    fun showProcessing() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoHideRunnable)
            if (currentState == VoiceOverlayState.IDLE) return@post

            currentState = VoiceOverlayState.PROCESSING
            tvOverlayIcon?.text = "⏳"
            tvOverlayStateBadge?.text = "ОБРАБОТКА"
            tvOverlayStateBadge?.setTextColor(ContextCompat.getColor(context, R.color.accent_yellow))
            Log.d(TAG, "Overlay state: PROCESSING")
        }
    }

    fun showResult(rawStt: String, cmdResult: CommandResult, latencyMs: Long, rtf: Float) {
        mainHandler.post {
            mainHandler.removeCallbacks(autoHideRunnable)
            if (!ensureViewInflated()) return@post
            attachViewIfNeeded()

            currentState = VoiceOverlayState.RESULT

            val isSuccess = cmdResult.success
            tvOverlayIcon?.text = if (isSuccess) "✓" else "✕"
            tvOverlayStateBadge?.text = if (isSuccess) "ВЫПОЛНЕНО" else "ОТКЛОНЕНО"
            tvOverlayStateBadge?.setTextColor(
                ContextCompat.getColor(context, if (isSuccess) R.color.accent_green else R.color.accent_red)
            )

            tvOverlayStt?.text = if (rawStt.isNotEmpty()) rawStt else "(Речь не распознана)"
            layoutOverlayResult?.visibility = View.VISIBLE
            tvOverlayIntentBadge?.text = "${cmdResult.command.type}:"
            tvOverlayResultText?.text = cmdResult.statusMessage
            tvOverlayResultText?.setTextColor(
                ContextCompat.getColor(context, if (isSuccess) R.color.text_mono else R.color.accent_red)
            )

            tvOverlayLatency?.text = "${latencyMs} ms | RTF: ${"%.2f".format(rtf)}"

            overlayView?.visibility = View.VISIBLE
            Log.d(TAG, "Overlay state: RESULT ('$rawStt' -> ${cmdResult.statusMessage})")

            mainHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
        }
    }

    fun showError(message: String) {
        mainHandler.post {
            mainHandler.removeCallbacks(autoHideRunnable)
            if (!ensureViewInflated()) return@post
            attachViewIfNeeded()

            currentState = VoiceOverlayState.ERROR

            tvOverlayIcon?.text = "⚠️"
            tvOverlayStateBadge?.text = "ОШИБКА"
            tvOverlayStateBadge?.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
            tvOverlayStt?.text = message
            layoutOverlayResult?.visibility = View.GONE
            tvOverlayLatency?.text = "—"

            overlayView?.visibility = View.VISIBLE
            Log.d(TAG, "Overlay state: ERROR ($message)")

            mainHandler.postDelayed(autoHideRunnable, ERROR_HIDE_DELAY_MS)
        }
    }

    fun hide() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoHideRunnable)
            currentState = VoiceOverlayState.IDLE
            overlayView?.visibility = View.GONE
            Log.d(TAG, "Overlay hidden (IDLE)")
        }
    }

    fun destroy() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoHideRunnable)
            currentState = VoiceOverlayState.IDLE
            val view = overlayView
            if (view != null && isViewAttached) {
                try {
                    windowManager.removeView(view)
                    Log.d(TAG, "Overlay view removed from WindowManager")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing overlay view", e)
                }
                isViewAttached = false
            }
            overlayView = null
        }
    }
}
