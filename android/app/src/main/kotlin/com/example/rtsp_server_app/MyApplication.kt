package com.example.rtsp_server_app

import android.app.Application
import androidx.multidex.MultiDex

class MyApplication : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}