package com.remotesend.app

import android.app.Application
import com.remotesend.app.di.AppContainer

class RemoteSendApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
