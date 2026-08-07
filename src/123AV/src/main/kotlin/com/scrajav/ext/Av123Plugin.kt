package com.scrajav.ext

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class Av123Plugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Av123Provider())
    }
}
