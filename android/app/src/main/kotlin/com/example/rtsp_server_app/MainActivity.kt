package com.example.rtsp_server_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.rtspserver.RtspServerCamera2
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : FlutterActivity(), ConnectChecker {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val CHANNEL = "com.example/rtsp_server"
    private var rtspServerCamera2: RtspServerCamera2? = null
    private val rtspPort = 8554
    private val httpPort = 8080
    private var detectionManager: DetectionManager? = null
    private var httpServer: HttpServer? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var isStreaming = false
    private var isDetectionRunning = false

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        detectionManager = DetectionManager(this)

        // Start HTTP server
        try {
            httpServer = HttpServer(httpPort, detectionManager!!)
            httpServer?.start()
            Log.d(TAG, "HTTP Server started on port $httpPort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP Server", e)
        }

        // Setup background thread for detection
        backgroundThread = HandlerThread("DetectionThread").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startServer" -> {
                        val isFront = call.argument<Boolean>("isFront") ?: false
                        if (checkPermissions()) {
                            val ip = startRtspServer(isFront)
                            if (ip != null) {
                                result.success("rtsp://$ip:$rtspPort")
                            } else {
                                result.error("ERROR", "Failed to start server", null)
                            }
                        } else {
                            requestPermissions()
                            result.error("PERMISSION", "Permissions not granted", null)
                        }
                    }
                    "stopServer" -> {
                        stopRtspServer()
                        result.success("Stopped")
                    }
                    "getIpAddress" -> {
                        result.success(getIpAddress())
                    }
                    "getMotionState" -> {
                        result.success(detectionManager?.getMotionState() ?: 0)
                    }
                    "getAIState" -> {
                        val people = detectionManager?.getPeopleState() ?: 0
                        val vehicle = detectionManager?.getVehicleState() ?: 0
                        val dogCat = detectionManager?.getDogCatState() ?: 0
                        val face = detectionManager?.getFaceState() ?: 0
                        result.success(mapOf(
                            "people" to people,
                            "vehicle" to vehicle,
                            "dog_cat" to dogCat,
                            "face" to face
                        ))
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun startRtspServer(isFront: Boolean): String? {
        try {
            if (rtspServerCamera2 == null) {
                rtspServerCamera2 = RtspServerCamera2(this, this, rtspPort)
            }

            if (!isStreaming) {
                // Prepare video and audio
                if (rtspServerCamera2?.prepareAudio() == true &&
                    rtspServerCamera2?.prepareVideo(640, 480, 15, 1000 * 1024, 0) == true) {

                    // Switch camera if needed
                    if (rtspServerCamera2?.isFrontCamera != isFront) {
                        rtspServerCamera2?.switchCamera()
                    }

                    // Start streaming
                    rtspServerCamera2?.startStream()
                    isStreaming = true
                    Log.d(TAG, "RTSP server started")

                    // Start the frame extraction loop for AI Detection
                    startFrameCaptureLoop()

                    return getIpAddress()
                } else {
                    Log.e(TAG, "Failed to prepare audio/video")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start RTSP server failed", e)
        }
        return null
    }

    /**
     * GL Interface ကနေ live frame တွေကို Bitmap အနေနဲ့ extract လုပ်မယ့် loop စနစ်
     */
    private fun startFrameCaptureLoop() {
        if (isDetectionRunning) return
        isDetectionRunning = true
        captureNextFrame()
    }

    private fun stopFrameCaptureLoop() {
        isDetectionRunning = false
    }

    private fun captureNextFrame() {
        if (!isStreaming || !isDetectionRunning) return

        try {
            // PedroSG94 library ရဲ့ GL Surface ကနေ လက်ရှိ frame ကို snapshot ရိုက်ယူခြင်း
            rtspServerCamera2?.glInterface?.takePhoto { bitmap ->
                if (bitmap != null) {
                    backgroundHandler?.post {
                        // DetectionManager ဆီသို့ Bitmap အတိုင်း တိုက်ရိုက်ပို့ပေးခြင်း
                        detectionManager?.processBitmapFrame(bitmap)

                        // ခဏနားပြီး နောက် Frame တစ်ခု ထပ်ဖမ်းရန် loop ပတ်ခြင်း (~10 FPS နှုန်းဖြင့် ဖမ်းယူရန် 100ms သတ်မှတ်ထား)
                        if (isDetectionRunning) {
                            backgroundHandler?.postDelayed({ captureNextFrame() }, 100)
                        }
                    }
                } else {
                    // ကင်မရာ Frame မတက်သေးပါက 200ms အကြာတွင် ထပ်ကြိုးစားရန်
                    if (isDetectionRunning) {
                        backgroundHandler?.postDelayed({ captureNextFrame() }, 200)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during takeSnapshot loop", e)
            if (isDetectionRunning) {
                backgroundHandler?.postDelayed({ captureNextFrame() }, 500)
            }
        }
    }

    private fun stopRtspServer() {
        try {
            stopFrameCaptureLoop()
            if (isStreaming) {
                rtspServerCamera2?.stopStream()
                isStreaming = false
            }
            rtspServerCamera2 = null
            detectionManager?.cleanup()
            Log.d(TAG, "RTSP server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping RTSP server", e)
        }
    }

    private fun getIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (networkInterface.name.startsWith("wlan") ||
                    networkInterface.name.startsWith("eth") ||
                    networkInterface.name.startsWith("en")) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress) {
                            val sAddr = address.hostAddress
                            if (sAddr.indexOf(':') < 0 && sAddr.startsWith("192.168")) {
                                Log.d(TAG, "Found IP: $sAddr")
                                return sAddr
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get IP error", e)
        }
        return "127.0.0.1"
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET
            ),
            1
        )
    }

    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {}
    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "RTSP connection failed: $reason")
        stopRtspServer()
    }
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() {}
    override fun onAuthError() {}
    override fun onAuthSuccess() {}

    override fun onDestroy() {
        super.onDestroy()
        stopRtspServer()
        httpServer?.stop()
        detectionManager?.cleanup()
        backgroundThread?.quitSafely()
    }
}