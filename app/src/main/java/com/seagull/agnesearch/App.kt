package com.seagull.agnesearch

import android.app.Application
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.net.Http

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Http.applyProxy(Store.settings.value.proxyUrl)
    }
}
