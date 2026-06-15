package com.everett.motionextractor

import android.app.Application

class MotionExtractorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ErrorReporter.init(this)
    }
}
