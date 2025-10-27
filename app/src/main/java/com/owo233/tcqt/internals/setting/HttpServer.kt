package com.owo233.tcqt.internals.setting

import fi.iki.elonen.NanoHTTPD

class HttpServer(
    host: String,
    port: Int,
    private val htmlContent: String
) : NanoHTTPD(host, port) {

    override fun serve(session: IHTTPSession?): Response {
        val uri = session?.uri ?: "/"
        return if (uri == "/" || uri.isEmpty()) {
            newFixedLengthResponse(Response.Status.OK, "text/html", htmlContent)
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found")
        }
    }
}
