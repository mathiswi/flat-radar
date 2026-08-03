package dev.flatradar.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.server.response.respond
import java.net.InetAddress

class IpWhitelistConfig {
    var excludePaths: List<String> = emptyList()
}

val IpWhitelist = createApplicationPlugin(
    name = "IpWhitelist",
    createConfiguration = ::IpWhitelistConfig,
) {
    val excludePaths = pluginConfig.excludePaths

    onCall { call ->
        val path = call.request.path()
        if (path in excludePaths) return@onCall

        val remoteStr = call.request.local.remoteAddress
        val addr = try {
            InetAddress.getByName(remoteStr)
        } catch (_: Exception) {
            null
        }

        if (addr == null || (!addr.isLoopbackAddress && !addr.isSiteLocalAddress)) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "access denied"))
        }
    }
}
