package com.antecer.nekopaw.api

import de.prosiebensat1digital.oasisjsbridge.*
import kotlinx.coroutines.cancel
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder

/**
 *建立 JS 引擎,并加载附加模块
 */
@Suppress("unused")
class JsEngine private constructor() {
    companion object {
        val ins: JsEngine by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            JsEngine()
        }
    }

    // 存储多个 JsManager 给不同线程调用
    private val jsEngineMap = mutableMapOf<String, JsManager>()

    /**
     * 调度或新建指定Tag的JsBridge
     */
    fun tag(t: String): JsManager {
        return jsEngineMap.getOrPut(t) { JsManager() }
    }

    /**
     * 移除指定Tag的JsBridge并释放被占用的资源
     */
    fun remove(t: String) {
        jsEngineMap[t]?.let {
            it.disposeJsoup()
            it.jsBridge.cancel()
            jsEngineMap.remove(t)
        }
    }

    class JsManager {
        // 定义消息回调
        private var mListener: ((String) -> Unit)? = null
        fun setLogOut(listener: (String) -> Unit) {
            this.mListener = listener
        }

        // 创建 JsBridge 对象
        val jsBridge = JsBridge(JsBridgeConfig.bareConfig())

        // 创建 Jsoup 对象
        private val jsoupManager = JsoupToJS()

        // 释放 Jsoup 占用的资源
        fun disposeJsoup() {
            jsoupManager.dispose()
        }

        init {
            Timber.tag("JsEngine").d("JS引擎已加载!")

            // 注入默认顶级类
            jsBridge.evaluateNoRetVal("var global = globalThis; var window = globalThis;")

            // console 方法注入
            JsValue.fromNativeFunction2(jsBridge) { mode: String, msg: Any? ->
                Timber.tag("JsEngine").let { log ->
                    msg.toString().let {
                        when (mode[0].toLowerCase()) {
                            'v' -> log.v(it)
                            'i' -> log.i(it)
                            'w' -> log.w(it)
                            'e' -> log.e(it)
                            else -> log.d(it)
                        }
                    }
                }
                mListener?.invoke(msg.toString());
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

            // URLEncoder 方法注入为 encodeURI()
            JsValue.fromNativeFunction2(jsBridge) { src: String, enc: String? ->
                URLEncoder.encode(src, enc ?: "utf-8")
            }.assignToGlobal("encodeURI")
            jsBridge.evaluateBlocking<Any>(
                """
            String.prototype.encodeURI = function (charset) { return encodeURI(this, charset); }
            console.debug('URLEncoder 方法已注入为 encodeURI');
        """.trimIndent()
            )

            // URLDecoder 方法注入为 decodeURI()
            JsValue.fromNativeFunction2(jsBridge) { src: String, enc: String? ->
                URLDecoder.decode(src, enc ?: "utf-8")
            }.assignToGlobal("decodeURI")
            jsBridge.evaluateBlocking<Any>(
                """
            String.prototype.decodeURI = function (charset) { return decodeURI(this, charset); }
            console.debug('URLDecoder 方法已注入为 decodeURI');
        """.trimIndent()
            )

            // 注入日期格式化函数,用法:new Date().Format("yyyy-MM-dd hh:mm:ss.fff")
            jsBridge.evaluateBlocking<Any>(
                """
Date.prototype.format = function (exp) {
	let t = {
		'y+': this.getFullYear(), // 年
		'M+': this.getMonth() + 1, // 月
		'd+': this.getDate(), // 日
		'h+': this.getHours(), // 时
		'm+': this.getMinutes(), // 分
		's+': this.getSeconds(), // 秒
		'f+': this.getMilliseconds(), // 毫秒
		'q+': Math.floor(this.getMonth() / 3 + 1) // 季度
	};
	for (let k in t) {
		let m = exp.match(k);
		if (m) {
			switch (k) {
				case 'y+':
					exp = exp.replace(m[0], t[k].toString().substr(0 - m[0].length));
					break;
				case 'f+':
					exp = exp.replace(m[0], t[k].toString().padStart(3, 0).substr(0, m[0].length));
					break;
				default:
					exp = exp.replace(m[0], m[0].length == 1 ? t[k] : t[k].toString().padStart(m[0].length, 0));
			}
		}
	}
	return exp;
};
        """.trimIndent()
            )

            // OkHttp 方法注入为 fetch()
            OkHttpToJS.instance.binding(jsBridge, "fetch")
            // Jsoup 方法注入为 class Document()
            jsoupManager.binding(jsBridge, "Document")
        }
    }
}