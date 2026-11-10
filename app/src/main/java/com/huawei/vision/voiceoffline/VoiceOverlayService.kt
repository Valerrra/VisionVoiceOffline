package com.huawei.vision.voiceoffline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

interface ServiceTelemetryListener {
    fun onSessionStarted(source: String)
    fun onSessionFinished(metrics: SttMetrics, result: CommandResult?)
    fun onError(error: String)
    fun onModelReady(ready: Boolean)
}

class VoiceOverlayService : Service() {

    companion object {
        private const val TAG = "VoiceOverlayService"
        const val NOTIFICATION_ID = 4001
        const val CHANNEL_ID = "voice_overlay_service_channel"

        const val ACTION_START = "com.huawei.vision.voiceoffline.action.START_SERVICE"
        const val ACTION_STOP = "com.huawei.vision.voiceoffline.action.STOP_SERVICE"

        private const val ACTION_VOICE_ASSIST_KEY_DOWN = "com.huawei.key.action.VOICE_ASSIST_KEY_DOWN"
        private const val ACTION_VOICE_ASSIST_KEY_UP = "com.huawei.key.action.VOICE_ASSIST_KEY_UP"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var telemetryListener: ServiceTelemetryListener? = null

        fun start(context: Context) {
            val intent = Intent(context, VoiceOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    private lateinit var overlayWindowManager: VoiceOverlayWindowManager
    private lateinit var sttEngine: SttEngine
    private lateinit var audioCapture: RemoteAudioCapture
    private lateinit var commandRouter: CommandRouter

    private var sessionState = MainActivity.SessionState.IDLE
    private var lastSessionEndTimeMs = 0L
    private var keyPressTimeMs = 0L

    private val voiceKeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ACTION_VOICE_ASSIST_KEY_DOWN -> {
                    Log.i(TAG, "Broadcast received in Service: VOICE_ASSIST_KEY_DOWN")
                    handleVoiceStart("Huawei Broadcast KEY_DOWN")
                }
                ACTION_VOICE_ASSIST_KEY_UP -> {
                    Log.i(TAG, "Broadcast received in Service: VOICE_ASSIST_KEY_UP")
                    handleVoiceStop("Huawei Broadcast KEY_UP")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VoiceOverlayService onCreate()")

        overlayWindowManager = VoiceOverlayWindowManager(this)
        sttEngine = SttEngine(this)
        audioCapture = RemoteAudioCapture(this)
        commandRouter = CommandRouter(this)

        createNotificationChannel()
        val notification = buildForegroundNotification("Инициализация модели речи...")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service with type microphone", e)
            startForeground(NOTIFICATION_ID, notification)
        }

        registerVoiceKeyReceiver()

        Log.i(TAG, "Pre-warming Sherpa-ONNX model in Service...")
        sttEngine.init { success, msg ->
            Log.i(TAG, "Service STT init result: $success ($msg)")
            telemetryListener?.onModelReady(success)
            updateNotification(
                if (success) "Готов к приему команд (зажмите микрофон на пульте)"
                else "Ошибка загрузки модели STT"
            )
        }

        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Service received ACTION_STOP command")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun registerVoiceKeyReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_VOICE_ASSIST_KEY_DOWN)
            addAction(ACTION_VOICE_ASSIST_KEY_UP)
            priority = 1000
        }
        registerReceiver(voiceKeyReceiver, filter)
        Log.i(TAG, "VoiceAssist BroadcastReceiver registered in VoiceOverlayService")
    }

    @Synchronized
    private fun handleVoiceStart(source: String) {
        val now = SystemClock.elapsedRealtime()
        if (sessionState != MainActivity.SessionState.IDLE) {
            Log.w(TAG, "Ignoring duplicate KEY_DOWN ($source): state=$sessionState")
            return
        }

        if (now - lastSessionEndTimeMs < 150) {
            Log.w(TAG, "Debounce: KEY_DOWN ignored (diff: ${now - lastSessionEndTimeMs} ms)")
            return
        }

        if (!sttEngine.isReady) {
            Log.w(TAG, "STT model not ready yet")
            overlayWindowManager.showError("Модель еще загружается...")
            return
        }

        sessionState = MainActivity.SessionState.RECORDING
        keyPressTimeMs = now

        overlayWindowManager.showListening()
        telemetryListener?.onSessionStarted(source)
        sttEngine.startUtterance()

        val started = audioCapture.startCapture(
            onAudioChunk = { samples, rmsDb, peak ->
                sttEngine.feedSamples(samples) { partialText ->
                    if (sessionState == MainActivity.SessionState.RECORDING) {
                        overlayWindowManager.updatePartial(partialText, rmsDb, peak)
                    }
                }
            },
            onRouteConfirmed = { pref, routed ->
                Log.i(TAG, "Capture route confirmed: routed=${routed?.address} (id=${routed?.id})")
            },
            onError = { errMsg ->
                Log.e(TAG, "Capture error: $errMsg")
                overlayWindowManager.showError("Ошибка микрофона: $errMsg")
                sessionState = MainActivity.SessionState.IDLE
                lastSessionEndTimeMs = SystemClock.elapsedRealtime()
                telemetryListener?.onError(errMsg)
            }
        )

        if (!started) {
            sessionState = MainActivity.SessionState.IDLE
            lastSessionEndTimeMs = SystemClock.elapsedRealtime()
            overlayWindowManager.showError("Не удалось открыть микрофон пульта")
        }
    }

    @Synchronized
    private fun handleVoiceStop(source: String) {
        if (sessionState != MainActivity.SessionState.RECORDING) {
            Log.w(TAG, "Ignoring KEY_UP ($source): state=$sessionState")
            return
        }

        val keyUpTimeMs = SystemClock.elapsedRealtime()
        val pressDurationMs = keyUpTimeMs - keyPressTimeMs

        sessionState = MainActivity.SessionState.FINALIZING
        overlayWindowManager.showProcessing()

        Log.i(TAG, "KEY_UP ($source, hold ${pressDurationMs} ms). Graceful drain starting...")

        audioCapture.stopCaptureGracefully(RemoteAudioCapture.DEFAULT_TAIL_DRAIN_MS) { totalSamplesRead, timeFirst, timeLast, tailChunks ->
            sttEngine.finishUtterance(
                timeKeyDownMs = keyPressTimeMs,
                timeKeyUpMs = keyUpTimeMs,
                totalSamplesRead = totalSamplesRead,
                timeFirstChunkMs = timeFirst,
                timeLastChunkMs = timeLast,
                tailChunksCount = tailChunks
            ) { metrics ->
                val rawStt = metrics.finalTranscription.trim()
                Log.i(TAG, "STT finished: '$rawStt' (latency: ${metrics.postReleaseLatencyMs} ms, tail chunks: $tailChunks)")

                var cmdResult: CommandResult? = null
                if (rawStt.isNotEmpty() && !rawStt.startsWith("(")) {
                    val res = commandRouter.route(rawStt)
                    cmdResult = res
                    overlayWindowManager.showResult(
                        rawStt = rawStt,
                        cmdResult = res,
                        latencyMs = metrics.postReleaseLatencyMs,
                        rtf = metrics.rtf
                    )
                } else {
                    overlayWindowManager.showError("Речь не распознана или тишина")
                }

                telemetryListener?.onSessionFinished(metrics, cmdResult)

                sessionState = MainActivity.SessionState.IDLE
                lastSessionEndTimeMs = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VisionVoice Оверлей",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновая служба голосового ассистента поверх всех окон"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VisionVoice STT Оверлей")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "VoiceOverlayService onDestroy() - releasing all resources")
        isRunning = false

        try {
            unregisterReceiver(voiceKeyReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering voice receiver", e)
        }

        overlayWindowManager.destroy()
        audioCapture.stopCapture()
        sttEngine.release()

        stopForeground(true)
        Log.i(TAG, "VoiceOverlayService stopped completely")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
