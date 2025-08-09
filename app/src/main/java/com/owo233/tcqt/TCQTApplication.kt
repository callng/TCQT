package com.owo233.tcqt

import android.app.Application

class TCQTApplication: Application() {

    companion object {
        lateinit var instance: TCQTApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
