package com.doublethinksolutions.osp

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.doublethinksolutions.osp.network.NetworkClient
import com.doublethinksolutions.osp.signing.MediaSigner

class MyApplication : Application() {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate() {
        super.onCreate()

        // Initialize the network client with the application context
        NetworkClient.initialize(this)
        MediaSigner.initialize(this)
    }
}
