package com.scrajav.ext

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class JavggPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(JavggProvider())
    }
}
