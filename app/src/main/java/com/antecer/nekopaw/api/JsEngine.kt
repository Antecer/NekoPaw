package com.antecer.nekopaw.api

import android.util.Log
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.utils.MemoryManager
import io.alicorn.v8.V8JavaAdapter


class JsEngine {
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
        return jsEngineMap.getOrPut(t) { JsManager(t) }
    }

    /**
     * 移除指定Tag的JsBridge并释放被占用的资源
     */
    fun remove(t: String) {
        jsEngineMap[t]?.let {
            it.scope.release()
            JsoupToJS.ins.remove(t)
            jsEngineMap.remove(t)
        }
    }

    // 定义URI编码函数
    @Suppress("unused")
    object CharsetURI {
        fun encodeURI(src: String, enc: String?): String {
            return java.net.URLEncoder.encode(src, enc ?: "utf-8")
        }

        fun decodeURI(src: String, enc: String?): String {
            return java.net.URLDecoder.decode(src, enc ?: "utf-8")
        }
    }

    /**
     * 包装常用JS方法
     */
    class JsManager(t: String) {
        // 创建 JsBridge 对象
        val js: V8 = V8.createV8Runtime()
        val scope = MemoryManager(js)    //自动内存管理

        // 定义消息回调
        private var msgOutput: ((String) -> Unit)? = null
        fun setLogOut(listener: (String) -> Unit) {
            this.msgOutput = listener
        }

        init {
            Log.d("JsManager", "JS引擎已加载")
            // 包装默认默认JS顶级类
            js.executeVoidScript("var global = globalThis; var window = globalThis;")
            // 备注: 若params[n]为undefined,需要toString()再判断; 若params[n]为null,可以直接判断;
            // console 方法注入
            js.registerJavaMethod({ _, params ->
                val deg = params[0].toString()
                var tag = "JsRuntime"
                var msg = params[1].toString()
                params[2]?.toString()?.let {
                    tag = msg
                    msg = it
                }
                when (deg[0]) {
                    'l' -> Log.v(tag, msg)
                    'i' -> Log.i(tag, msg)
                    'w' -> Log.w(tag, msg)
                    'e' -> Log.e(tag, msg)
                    else -> Log.d(tag, msg)
                }
                msgOutput?.invoke(msg);
            }, "consoleKt")
            js.executeVoidScript(
                """
console = {
	debug: (...msg) => consoleKt('d', msg.shift(), msg.join() || null),
	log: (...msg) => consoleKt('l', msg.shift(), msg.join() || null),
	info: (...msg) => consoleKt('i', msg.shift(), msg.join() || null),
	warn: (...msg) => consoleKt('w', msg.shift(), msg.join() || null),
	error: (...msg) => consoleKt('e', msg.shift(), msg.join() || null)
};
console.log('Log 方法已注入为 console');
        """.trimIndent()
            )

            // 注入方法组 URLEncoder & URLDecoder
            V8JavaAdapter.injectObject("CharsetURI", CharsetURI, js);
            js.executeVoidScript(
                """
            var encodeURI = (src, enc) => { return CharsetURI.encodeURI(src, enc); };
            String.prototype.encodeURI = function (charset) { return CharsetURI.encodeURI(this, charset); };
            //console.debug('URLEncoder 方法已注入为 encodeURI');
            var decodeURI = (src, enc) => { return CharsetURI.decodeURI(src, enc); };
            String.prototype.decodeURI = function (charset) { return CharsetURI.decodeURI(this, charset); }
            //console.debug('URLDecoder 方法已注入为 decodeURI');
        """.trimIndent()
            )

            // 注入日期格式化函数,用法:new Date().Format("yyyy-MM-dd hh:mm:ss.fff")
            js.executeVoidScript(
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
            OkHttpToJS.ins.binding(js, "fetch")
            // Jsoup 方法注入为 class Document()
            JsoupToJS.ins.tag(t).binding(js, "Document")
        }
    }
}