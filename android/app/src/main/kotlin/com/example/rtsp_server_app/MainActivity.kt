package com.example.rtsp_server_app

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.rtspserver.RtspServerCamera2
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.net.NetworkInterface
import java.util.Collections

class MainActivity: FlutterActivity(), ConnectChecker {

    private val CHANNEL = "com.example/rtsp_server"
    private var rtspServerCamera2: RtspServerCamera2? = null
    private val port = 8554

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startServer" -> {
                    if (checkPermissions()) {
                        val ip = startRtspServer()
                        if (ip != null) {
                            result.success("rtsp://$ip:$port/live.sdp")
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
                else -> result.notImplemented()
            }
        }
    }

    private fun startRtspServer(): String? {
        if (rtspServerCamera2 == null) {
            rtspServerCamera2 = RtspServerCamera2(this, this, port)
        }

        if (rtspServerCamera2?.isStreaming == false) {
            if (rtspServerCamera2?.prepareAudio() == true && rtspServerCamera2?.prepareVideo(1280, 720, 30, 2000 * 1024, 0) == true) {
                rtspServerCamera2?.startStream()
                return getIpAddress()
            }
        }
        return null
    }

    private fun stopRtspServer() {
        if (rtspServerCamera2?.isStreaming == true) {
            rtspServerCamera2?.stopStream()
        }
    }

    private fun getIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress) {
                        val sAddr = address.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
        } catch (ex: Exception) { }
        return "127.0.0.1"
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {}
    override fun onConnectionFailed(reason: String) { stopRtspServer() }
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() {}
    override fun onAuthError() {}
    override fun onAuthSuccess() {}
}