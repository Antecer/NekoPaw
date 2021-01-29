package com.antecer.nekopaw.api

import android.widget.TextView
import de.prosiebensat1digital.oasisjsbridge.*
import timber.log.Timber
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/**
 *建立 JS 引擎,并加载附加模块
 */
class JsEngine private constructor() {
    companion object {
        val instance: JsEngine by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            JsEngine()
        }
    }

    // JS 引擎建立
    var jsBridge: JsBridge = JsBridge(JsBridgeConfig.bareConfig())

    // 绑定日志输出控件
    private var logView: TextView? = null
    val setLogout = { T: TextView -> logView = T }
    val clearLogView = { logView?.let { it.post { it.text = "" } } }

    // 打印日志到目标控件
    private var startTime: Long = 0
    val clearTimer = { startTime = System.currentTimeMillis() }
    private fun printToUI(T: Any) {
        logView?.let {
            it.post {
                val converter = SimpleDateFormat("[mm:ss.SSS]", Locale.getDefault())
                val msg = "${converter.format(Date(System.currentTimeMillis() - startTime))} $T"
                it.append("${msg}\n")
            }
        }
    }

    init {
        Timber.tag("JsEngine").d("JS引擎已加载!")

        // 注入默认顶级类
        jsBridge.evaluateNoRetVal("var global = globalThis; var window = globalThis;")

        // console 方法注入
        JsValue.fromNativeFunction2(jsBridge) { mode: String, msg: Any? ->
            when (mode[0].toLowerCase()) {
                'v' -> Timber.tag("JsEngine").v(msg?.toString() ?: "null")
                'i' -> Timber.tag("JsEngine").i(msg?.toString() ?: "null")
                'w' -> Timber.tag("JsEngine").w(msg?.toString() ?: "null")
                'e' -> Timber.tag("JsEngine").e(msg?.toString() ?: "null")
                else -> Timber.tag("JsEngine").d(msg?.toString() ?: "null")
            }
            printToUI(msg ?: "null")
        }.assignToGlobal("PrintLog")
        jsBridge.evaluateBlocking<Any>(
            """
                var console = {
                    debug: (msg) => PrintLog('d', msg),
                    log: (msg) => PrintLog('v', msg),
                    info: (msg) => PrintLog('i', msg),
                    warn: (msg) => PrintLog('w', msg),
                    error: (msg) => PrintLog('e', msg)
                };
                console.debug('Timber 方法已注入为 console');
            """.trimIndent()
        )

        // UrlEncoder 方法注入为 UrlEncoder()
        JsValue.fromNativeFunction2(jsBridge) { source: String, charset: String? ->
            URLEncoder.encode(source, charset ?: "utf-8")
        }.assignToGlobal("UrlEncoder")
        jsBridge.evaluateBlocking<Any>("console.debug('URLEncoder 方法已注入为 UrlEncoder')")


        // OkHttp 方法注入为 fetch()
        OkHttpToJS.instance.binding(jsBridge, "fetch")
        // Jsoup 方法注入为 class Document()
        JsoupToJS.instance.binding(jsBridge, "Document")
    }
}