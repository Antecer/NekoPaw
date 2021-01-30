package com.antecer.nekopaw.web

import com.antecer.nekopaw.api.JsEngine
import kotlinx.coroutines.CoroutineScope
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.io.IOException

class WebSocket(handshakeRequest: NanoHTTPD.IHTTPSession, uri: String) :
    NanoWSD.WebSocket(handshakeRequest), CoroutineScope by MainScope() {

    override fun onOpen() {
        launch(IO) {
            kotlin.runCatching {
                while (isOpen) {
                    ping("ping".toByteArray())
                    delay(30000)
                }
            }
        }
    }

    override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
        cancel()
        JsEngine.ins.remove("ws")
    }

    init {
        JsEngine.ins.tag("ws").setLogOut { msg -> send(msg) }
    }

    private val otherUri = uri

    override fun onMessage(message: NanoWSD.WebSocketFrame) {
        if (otherUri == "/runJS") {
            val js = message.textPayload
            if (js.isEmpty()) return
            launch(IO) {
                kotlin.runCatching {
                    JsEngine.ins.tag("ws").jsBridge.evaluate<Any>(js)
                    send("--执行完成--")
                }.onFailure {
                    send(it.stackTraceToString())
                    it.printStackTrace()
                }
            }
        }
    }

    override fun onPong(pong: NanoWSD.WebSocketFrame) {

    }

    override fun onException(exception: IOException) {

    }

}