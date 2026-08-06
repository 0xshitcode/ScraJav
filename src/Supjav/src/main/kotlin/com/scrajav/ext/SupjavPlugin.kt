package com.scrajav.ext

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SupjavPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(SupjavProvider())
    }
}
