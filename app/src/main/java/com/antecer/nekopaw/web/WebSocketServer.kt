package com.antecer.nekopaw.web

import fi.iki.elonen.NanoWSD

class WebSocketServer(port: Int) : NanoWSD(port) {
    override fun openWebSocket(handshake: IHTTPSession) = WebSocket(handshake, handshake.uri)
}