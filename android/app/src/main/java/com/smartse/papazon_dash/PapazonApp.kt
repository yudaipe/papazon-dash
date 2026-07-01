package com.smartse.papazon_dash

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PapazonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:594000000000:android:papazonv6poc")
                .setApiKey("AIzaSyPapazonDashV6PoCBuildOnlyKey")
                .setProjectId("papazon-dash-poc")
                .setStorageBucket("papazon-dash-poc.appspot.com")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
