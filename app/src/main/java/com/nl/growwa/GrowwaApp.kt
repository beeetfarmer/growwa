package com.nl.growwa

import android.app.Application
import com.nl.growwa.core.di.AppContainer

class GrowwaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
