package com.example.rtsp_server_app

import android.util.Log
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class HttpServer(
    private val port: Int,
    private val detectionManager: DetectionManager
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "HttpServer"
        private val gson = Gson()
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            val uri = session.uri
            val method = session.method.name

            Log.d(TAG, "HTTP Request: $method $uri")

            when {
                uri.startsWith("/api.cgi") -> handleApiRequest(session)
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain",
                    "404 Not Found"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP Server Error", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "500 Internal Server Error: ${e.message}"
            )
        }
    }

    private fun handleApiRequest(session: IHTTPSession): Response {
        val params = session.parameters
        val cmd = params["cmd"]?.firstOrNull() ?: ""

        Log.d(TAG, "API Command: $cmd")

        val response = when (cmd) {
            "GetMdState" -> getMotionStateResponse()
            "GetAiState" -> getAIStateResponse()
            else -> {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    """[{"cmd":"$cmd","code":-1,"value":{"error":"Unknown command"}}]"""
                )
            }
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response
        )
    }

    private fun getMotionStateResponse(): String {
        try {
            val state = detectionManager.getMotionState()
            return """[{"cmd":"GetMdState","code":0,"value":{"state":$state}}]"""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting motion state", e)
            return """[{"cmd":"GetMdState","code":-1,"value":{"state":0}}]"""
        }
    }

    private fun getAIStateResponse(): String {
        try {
            val people = detectionManager.getPeopleState()
            val vehicle = detectionManager.getVehicleState()
            val dogCat = detectionManager.getDogCatState()
            val face = detectionManager.getFaceState()

            return """[{"cmd":"GetAiState","code":0,"value":{
                "channel":0,
                "dog_cat":{"alarm_state":$dogCat,"support":1},
                "face":{"alarm_state":$face,"support":0},
                "people":{"alarm_state":$people,"support":1},
                "vehicle":{"alarm_state":$vehicle,"support":1}
            }}]""".replace("\n", "").replace(" ", "")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting AI state", e)
            return """[{"cmd":"GetAiState","code":-1,"value":{}}]"""
        }
    }

    @Throws(IOException::class)
    override fun start() {
        super.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        Log.d(TAG, "HTTP Server started on port $port")
    }
}