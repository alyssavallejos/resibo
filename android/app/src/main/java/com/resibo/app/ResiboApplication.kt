package com.resibo.app

import android.app.Application
import com.google.firebase.FirebaseApp

class ResiboApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
