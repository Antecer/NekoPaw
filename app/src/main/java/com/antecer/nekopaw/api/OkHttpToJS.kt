package com.antecer.nekopaw.api

import android.os.SystemClock.sleep
import de.prosiebensat1digital.oasisjsbridge.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


/**
 * 连接 OkHttp 和 QuickJS(JsBridge)
 */
@Suppress("unused")
class OkHttpToJS private constructor() {
    companion object {
        val instance: OkHttpToJS by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            OkHttpToJS()
        }
    }

    // 创建 OkHttp 对象
    val client: OkHttpClient =
        OkHttpClient().newBuilder().connectTimeout(3, TimeUnit.SECONDS).build()

    /**
     * 包装 okHttp 方法
     */
    private val okHttpKtApi = object : JsToNativeInterface {
        /**
         * 顺序网络请求
         * @param url fetch请求网址
         * @param params fetch请求参数
         */
        fun fetch(url: String, params: JsonObjectWrapper?): String {
            var finalUrl = url
            var status = ""
            var statusText = ""
            var error = ""
            var success = ""
            var responseBody = ""  // 保存请求成功的 response.body().string()
            try {
                // 设置请求模式
                var method = "GET"
                // 设置默认 User-Agent
                val defAgent =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36"
                // 创建请求头构造器
                val headerBuilder = Headers.Builder().add("user-agent", defAgent)
                // 设置请求类型
                var mediaType = "application/x-www-form-urlencoded".toMediaType()
                // 创建请求体
                var sendBody = ""
                // 指定返回页面解码字符集(自动判断可能不准确)
                var charset: Charset? = null
                // 分析请求参数
                params?.toPayloadObject()?.let { paramMap ->
                    for (key in paramMap.keys) {
                        when (key.toLowerCase(Locale.US)) {
                            "charset" -> paramMap.getString(key)?.let { charset = Charset.forName(it) }
                            "method" -> paramMap.getString(key)?.let { method = it }
                            "body" -> paramMap.getString(key)?.let { sendBody = it }
                            "headers" -> {
                                paramMap.getObject(key)?.let { headers ->
                                    for (head in headers.keys) {
                                        headers.getString(head)?.let { value ->
                                            when (head.toLowerCase(Locale.US)) {
                                                "content-type" -> mediaType = value.toMediaType()
                                                "user-agent" -> headerBuilder.removeAll(head).add(
                                                    head,
                                                    value
                                                )
                                                else -> headerBuilder.add(head, value)
                                            }
                                        }
                                    }
                                }
                            }
                            else -> paramMap.getString(key)?.let { headerBuilder.add(key, it) }
                        }
                    }
                }
                val requestBuilder = Request.Builder().url(url).headers(headerBuilder.build())
                when (method) {
                    "POST" -> requestBuilder.post(sendBody.toRequestBody(mediaType))
                    else -> requestBuilder.get()
                }
                val response = client.newCall(requestBuilder.build()).execute()
                if (response.code != 200) {
                    val callBody = if (method == "GET") "" else "?$sendBody"
                    Timber.tag("OkHttp").d("[${response.code}] $method: $url$callBody")
                }

                responseBody = response.body?.let { body -> charset?.let { String(body.bytes(), it) } ?: body.string() } ?: ""
                finalUrl = response.request.url.toString()
                status = response.code.toString()
                statusText = response.message
                success = "ok"
            } catch (e: SocketTimeoutException) {
                Timber.tag("OkHttp").w("[TimeOut] ($url): $e")
                error = "timeout"
            } catch (t: Throwable) {
                t.printStackTrace()
                Timber.tag("OkHttp").e("[ERROR] ($url): $t")
                error = t.message ?: "Fetch出现未知错误"
            }
            val json = JSONObject()
            json.put("finalUrl", finalUrl)
            json.put("status", status)
            json.put("statusText", statusText)
            json.put("error", error)
            json.put("success", success)
            json.put("result", responseBody)
            return json.toString()
        }

        /**
         * 并发网络请求
         * @param actions fetch请求参数,例: [[[url,{...params}]],...]
         * @param retryNum 请求失败的重试次数
         */
        fun fetchAll(
            actions: JsonObjectWrapper,
            retryNum: Int = 3,
            multiCall: Int = 5
        ): Array<String?> {
            val retrySet = if (retryNum > 0) retryNum else 3    // 设置重试次数
            val multiCal = if (multiCall > 0) multiCall else 5  // 设置并发连接数
            client.dispatcher.maxRequestsPerHost = multiCal     // 修改 okHttp 并发数(默认5)
            val urlList = ArrayList<String?>()
            val methodList = ArrayList<String>()
            val headersList = ArrayList<Headers>()
            val mediaTypeList = ArrayList<MediaType>()
            val sendBodyList = ArrayList<String>()
            val charsetList = ArrayList<Charset?>()
            // 解析网络请求参数
            val readParams = { work: PayloadArray? ->
                // 设置请求模式
                var method = "GET"
                // 设置默认 User-Agent
                val defAgent =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36"
                // 创建请求头构造器
                val headersBuilder = Headers.Builder().add("user-agent", defAgent)
                // 设置请求类型
                var mediaType = "application/x-www-form-urlencoded"
                // 创建请求体
                var sendBody = ""
                // 指定返回页面解码字符集(自动判断可能不准确)
                var charset: Charset? = null
                // 分析请求参数
                work?.getObject(1)?.let { params ->
                    for (key in params.keys) {
                        when (key.toLowerCase(Locale.US)) {
                            "charset" -> params.getString(key)?.let { charset = Charset.forName(it) }
                            "method" -> params.getString(key)?.let { method = it }
                            "body" -> params.getString(key)?.let { sendBody = it }
                            "headers" -> {
                                params.getObject(key)?.let { headers ->
                                    for (head in headers.keys) {
                                        headers.getString(head)?.let { value ->
                                            when (head.toLowerCase(Locale.US)) {
                                                "content-type" -> mediaType = value
                                                "user-agent" -> headersBuilder.removeAll(head).add(
                                                    head,
                                                    value
                                                )
                                                else -> headersBuilder.add(head, value)
                                            }
                                        }
                                    }
                                }
                            }
                            else -> params.getString(key)?.let { headersBuilder.add(key, it) }
                        }
                    }
                }
                // 保存取得的值
                urlList.add(work?.getString(0))
                methodList.add(method)
                headersList.add(headersBuilder.build())
                mediaTypeList.add(mediaType.toMediaType())
                sendBodyList.add(sendBody)
                charsetList.add(charset)
            }

            try {
                val actionArr = actions.toPayloadArray()
                val actionCount = actionArr?.count ?: 0
                // 存储请求成功的字符串
                val resultsText = arrayOfNulls<String>(actionCount)
                var actionsStep = actionCount;
                /**
                 * 发起异步网络请求
                 */
                fun callAsync(request: Request, resIndex: Int, retryCount: Int) {
                    client.newCall(request).enqueue(object : Callback {
                        // 请求成功的回调函数
                        override fun onResponse(call: Call, response: Response) {
                            if (response.code == 200) {
                                Timber.tag("OkHttpAsync").i("[200] ${response.request.url}?${sendBodyList[resIndex]}")

                                resultsText[resIndex] = response.body?.let { body ->
                                    val charset = charsetList[resIndex]
                                    charset?.let { String(body.bytes(), it) } ?: body.string()
                                } ?: ""
                                --actionsStep
                            } else {
                                Timber.tag("OkHttpAsync").i("[${response.code}] ${request.url}?${sendBodyList[resIndex]}\n${response.message}")
                                val retryAgain = retryCount - 1
                                if (retryAgain > 0) {
                                    sleep(100); callAsync(request, resIndex, retryAgain)
                                } else {
                                    --actionsStep
                                }
                            }
                        }

                        // 网络错误的回调函数
                        override fun onFailure(call: Call, e: IOException) {
                            Timber.tag("OkHttpAsync").i("[Failed] ${request.url}?${sendBodyList[resIndex]}\n$e")
                            val retryAgain = retryCount - 1
                            if (retryAgain > 0) {
                                sleep(100); callAsync(request, resIndex, retryAgain)
                            } else {
                                --actionsStep
                            }
                        }
                    })
                }

                // 循环添加网络请求任务
                for (i in 0 until actionCount) {
                    readParams(actionArr?.getArray(i))
                    urlList[i]?.let { url ->
                        val requestBuilder = Request.Builder().url(url).headers(headersList[i])
                        when (methodList[i]) {
                            "POST" -> requestBuilder.post(
                                sendBodyList[i].toRequestBody(
                                    mediaTypeList[i]
                                )
                            )
                            else -> requestBuilder.get()
                        }
                        callAsync(requestBuilder.build(), i, retrySet)
                    }
                }
                // 等待网络请求完成
                while (actionsStep > 0) sleep(100)
                return resultsText
            } catch (t: Throwable) {
                t.printStackTrace()
                Timber.tag("OkHttpAsync").e("[ERROR] $t")
            }
            return arrayOfNulls(actions.toPayloadArray()?.count ?: 1)
        }
    }

    /**
     * 绑定到 JsBridge 对象
     * @param jsBridge 目标对象名称
     * @param name 注入到js内的名称
     */
    fun binding(jsBridge: JsBridge, apiName: String = "fetch") {
        // 注入 okHttp
        JsValue.fromNativeObject(jsBridge, okHttpKtApi).assignToGlobal("GlobalOkHttp")
        // 包装 js 方法
        val jsAPI = """
             class GlobalFetch {
                constructor(url, params) {
                    let FetchRes = JSON.parse(GlobalOkHttp.fetch(url, params));
                    this.finalUrl = FetchRes.finalUrl;
                    this.status = FetchRes.status;
                    this.statusText = FetchRes.statusText;
                    this.error = FetchRes.error;
                    this.success = FetchRes.success;
                    this.result = FetchRes.result;
                }
                text() { return this.result; }
                json() { return JSON.parse(this.result); }
            }
            const $apiName = (url, params) => new GlobalFetch(url, params || null);
            const ${apiName}All = (fetchArray, retryNum, multiCall) => GlobalOkHttp.fetchAll(fetchArray, retryNum, multiCall);
            console.debug('OkHttp 方法已注入为 $apiName');
        """.trimIndent()
        // 注入 js 包装的方法
        jsBridge.evaluateBlocking<Any>(jsAPI)
    }
}