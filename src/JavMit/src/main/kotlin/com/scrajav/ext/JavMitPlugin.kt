package com.scrajav.ext

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class JavMitPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(JavMitProvider())
    }
}
