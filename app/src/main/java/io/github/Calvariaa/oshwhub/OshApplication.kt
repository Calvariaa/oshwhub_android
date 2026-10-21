package io.github.Calvariaa.oshwhub

import android.app.Application
import io.github.Calvariaa.oshwhub.data.AppContainer

class OshApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
