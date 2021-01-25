package com.antecer.nekopaw.api

import de.prosiebensat1digital.oasisjsbridge.*
import okhttp3.*
import timber.log.Timber
import java.net.SocketTimeoutException
import java.util.*

/**
 * 连接Jsoup和QuickJS(JsBridge)
 */
class OkHttpToJS private constructor() {
    companion object {
        val instance: OkHttpToJS by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            OkHttpToJS()
        }
    }

    // 创建OkHttp对象
    val client: OkHttpClient = OkHttpClient()

    /**
     * 绑定到JsBridge对象
     * @param jsBridge 目标对象名称
     * @param name 注入到js内的名称
     */
    fun binding(jsBridge: JsBridge, apiName: String = "fetch") {
        // 修改okHttp并发数
        client.dispatcher().maxRequestsPerHost = 10
        Timber.tag("OkHttp").d("-> OkHttp 注入 JsEngine")
        val okHttpKtApi = object : JsToNativeInterface {
            var status: Int? = null
            var statusText: String? = null
            var error: String? = null
            var text: String? = null
            var success: String? = null

            // 模拟fetch请求
            fun fetch(url: String, params: JsonObjectWrapper?): JsonObjectWrapper {
                this.status = null
                this.statusText = null
                this.error = null
                this.text = null
                this.success = null
                try {
                    var request = Request.Builder().url(url)
                    val paramMap = params?.toPayloadObject()
                    // 设置 user-agent
                    val defAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36"
                    val userAgent = paramMap?.getObject("headers")?.getString("user-agent")
                    request = request.removeHeader("User-Agent").addHeader("User-Agent", userAgent ?: defAgent)
                    // 设置 Referer
                    val referer = paramMap?.getString("Referer")
                    if (referer != null) request = request.addHeader("Referer", referer)
                    // 设置 content-type
                    val mediaType = MediaType.parse(
                        paramMap?.getObject("headers")?.getString("content-type") ?: "application/json;charset=UTF-8"
                    )
                    // 设置 body
                    val sendData = paramMap?.getString("body") ?: ""
                    val requestBody = RequestBody.create(mediaType, sendData);
                    // 设置 method(请求模式)
                    val method = (paramMap?.getString("method") ?: "GET").toUpperCase(Locale.ROOT)
                    request = if (method == "GET") request.get() else request.post(requestBody)
                    // 发送请求
                    val response = client.newCall(request.build()).execute()

                    this.status = response.code()
                    this.statusText = response.message()
                    this.text = response.body()?.string()
                    this.success = "ok"

                    Timber.tag("OkHttp").d("[Success] $method: $url?$sendData")
                } catch (e: SocketTimeoutException) {
                    Timber.tag("OkHttp").w("[TimeOut] ($url): $e")
                    this.error = "timeout"
                } catch (t: Throwable) {
                    t.printStackTrace()
                    Timber.tag("OkHttp").e("[ERROR] ($url): $t")
                    this.error = t.message ?: "Fetch出现未知错误"
                }
                return JsonObjectWrapper(
                    "status" to this.status,
                    "statusText" to this.statusText,
                    "error" to this.error,
                    "success" to this.success
                )
            }

            fun text(): String? {
                return text
            }
        }
        JsValue.fromNativeObject(jsBridge, okHttpKtApi).assignToGlobal("GlobalOkHttp")
        val jsAPI = """
            class GlobalFetch {
                status;
                statusText;
                error;
                success;
                #body;
                constructor(url, params) {
                    let Gres = GlobalOkHttp.fetch(url, params);
                    this.status = Gres.status;
                    this.statusText = Gres.statusText;
                    this.error = Gres.error;
                    this.success = Gres.success;
                    this.#body = GlobalOkHttp.text();
                }
                text() { return this.#body || ''; }
                json() { return this.#body ? JSON.parse(this.#body) : ''; }
            }
            const $apiName = (url, params) => new GlobalFetch(url, params || null);
            console.debug('OkHttp 方法已注入为 $apiName');
        """.trimIndent()
        jsBridge.evaluateBlocking<Any>(jsAPI)
    }
}