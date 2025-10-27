package com.owo233.tcqt.internals.setting

object LocalWebServer {

    private var server: HttpServer? = null

    val settingUrlPair by lazy { parseHostAndPort(TCQTSetting.getSettingUrl()) }

    fun parseHostAndPort(url: String): Pair<String, Int> {
        val cleanUrl = url.removePrefix("http://").removePrefix("https://")
        val hostPort = cleanUrl.split("/").firstOrNull() ?: error("无效的URL")
        val parts = hostPort.split(":")
        require(parts.size == 2) { "无效的Url配置：必须为 host:port 格式" }
        val host = parts[0]
        val port = parts[1].toIntOrNull() ?: error("无效的port")
        require(host.isNotBlank()) { "host不能为空" }
        require(port in 0..65535) { "port必须在 0~65535 范围内" }
        return host to port
    }

    fun start(): Boolean {
        if (server != null && server!!.isAlive) {
            return true
        }

        return try {
            val html = TCQTSetting.getSettingHtml()
            server = HttpServer(
                settingUrlPair.first,
                settingUrlPair.second,
                html
            ).apply { start() }
            true
        } catch (e: java.io.IOException) {
            if (e is java.net.BindException || e.message?.contains("Address already in use") == true) {
                true
            } else {
                throw e
            }
        }
    }

    fun stop() {
        server?.stop()
        server = null
    }
}
