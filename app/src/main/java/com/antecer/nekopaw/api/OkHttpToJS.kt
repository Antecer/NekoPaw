package com.antecer.nekopaw.api

import android.os.SystemClock.sleep
import android.util.Log
import com.eclipsesource.v8.V8
import io.alicorn.v8.V8JavaAdapter
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


/**
 * 连接 OkHttp 和 JsEngine
 */
@Suppress("unused")
class OkHttpToJS private constructor() {
    companion object {
        val ins: OkHttpToJS by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            OkHttpToJS()
        }
    }

    // 创建 OkHttp 对象
    private val client: OkHttpClient = OkHttpClient().newBuilder().connectTimeout(5, TimeUnit.SECONDS).build()

    // 定义消息回调
    private var msgOutput: ((String) -> Unit)? = null
    fun setLogOut(listener: (String) -> Unit) {
        this.msgOutput = listener
    }

    private fun printLog(msg: String) {
        msgOutput?.invoke(msg);
    }

    /**
     * 单次网络请求
     * @param url fetch请求网址
     * @param params fetch请求参数
     */
    fun fetchKt(url: String, params: String?): String {
        var finalUrl = url
        var status = ""
        var statusText = ""
        var error = ""
        var text = ""  // 保存请求成功的 response.body().string()

        try {
            // 设置请求模式
            var method = "GET"
            // 设置默认 User-Agent
            val defAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36"
            // 创建请求头构造器
            val headerBuilder = Headers.Builder().add("user-agent", defAgent)
            // 设置请求类型
            var mediaType = "application/x-www-form-urlencoded"
            // 创建请求体
            var sendBody = ""
            // 指定返回页面解码字符集(自动判断可能不准确)
            var charset: Charset? = null
            // 分析请求参数
            params?.let { it ->
                val paramMap = JSONObject(it)
                for (key in paramMap.keys()) {
                    when (key.toLowerCase(Locale.US)) {
                        "charset" -> charset = Charset.forName(paramMap.optString(key, "utf-8"))
                        "method" -> method = paramMap.optString(key, method)
                        "body" -> sendBody = paramMap.optString(key, sendBody)
                        "headers" -> {
                            paramMap.optJSONObject(key)?.let { headers ->
                                for (head in headers.keys()) {
                                    headers.optString(head).let { value ->
                                        when (head.toLowerCase(Locale.US)) {
                                            "content-type" -> mediaType = value
                                            "user-agent" -> headerBuilder.removeAll(head).add(head, value)
                                            else -> headerBuilder.add(head, value)
                                        }
                                    }
                                }
                            }
                        }
                        else -> headerBuilder.add(key, paramMap.optString(key))
                    }
                }
            }
            val requestBuilder = Request.Builder().url(url).headers(headerBuilder.build())
            when (method) {
                "POST" -> requestBuilder.post(sendBody.toRequestBody(mediaType.toMediaType()))
                else -> requestBuilder.get()
            }
            val response = client.newCall(requestBuilder.build()).execute()
            if (response.code != 200) {
                val callBody = if (method == "GET") "" else "?$sendBody"
                Log.d("OkHttp", "[${response.code}] $method: $url$callBody")
            }
            // 识别Charset并解码内容
            text = response.body?.let { body ->
                val bodyBytes = body.bytes()
                charset = charset ?: body.contentType()?.charset()
                if (charset == null) {
                    val initText = String(bodyBytes).toLowerCase(Locale.US)
                    var charIndex = initText.indexOf("charset=")
                    if (charIndex > 0) {
                        charIndex += if (initText[charIndex + 8] == '"') 9 else 8
                        val charEnd = initText.indexOf('"', charIndex)
                        if (charEnd > 0) charset = Charset.forName(initText.substring(charIndex, charEnd))
                    }
                }
                String(bodyBytes, charset ?: Charsets.UTF_8)
            } ?: ""
            finalUrl = response.request.url.toString()
            status = response.code.toString()
            statusText = if (status == "200") "ok" else response.message
        } catch (t: Throwable) {
            t.printStackTrace()
            error = t.stackTraceToString()
            Log.d("OkHttp", "[ERROR] ($url): $error")
        } finally {
            val json = JSONObject()
            json.put("finalUrl", finalUrl)
            json.put("status", status)
            json.put("statusText", statusText)
            json.put("error", error)
            json.put("text", text)
            return json.toString()
        }
    }

    /**
     * 并发网络请求
     * @param actions fetch请求参数,例: [[[url,{...params}]],...]
     * @param retryNum 请求失败的重试次数
     * @return 请求结果组成的数组，失败的请求填充空字符串""
     */
    fun fetchAllKt(inputActions: String, retryNum: Int?, multiCall: Int?): Array<String?> {
        val retrySet = retryNum ?: 3    // 设置重试次数
        client.dispatcher.maxRequestsPerHost = multiCall ?: 5   // 修改 okHttp 并发数(默认5)
        var maxTimeOut = 300
        val urlList = ArrayList<String?>()
        val methodList = ArrayList<String>()
        val headersList = ArrayList<Headers>()
        val mediaTypeList = ArrayList<MediaType>()
        val sendBodyList = ArrayList<String>()
        val charsetList = ArrayList<Charset?>()
        // 解析网络请求参数
        val readParams = { work: JSONArray? ->
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
            work?.optJSONObject(1)?.let { paramMap ->
                for (key in paramMap.keys()) {
                    when (key.toLowerCase(Locale.US)) {
                        "charset" -> charset = Charset.forName(paramMap.optString(key, "utf-8"))
                        "method" -> method = paramMap.optString(key, method)
                        "body" -> sendBody = paramMap.optString(key, sendBody)
                        "headers" -> {
                            paramMap.optJSONObject(key)?.let { headers ->
                                for (head in headers.keys()) {
                                    headers.optString(head).let { value ->
                                        when (head.toLowerCase(Locale.US)) {
                                            "content-type" -> mediaType = value
                                            "user-agent" -> headersBuilder.removeAll(head).add(head, value)
                                            else -> headersBuilder.add(head, value)
                                        }
                                    }
                                }
                            }
                        }
                        else -> headersBuilder.add(key, paramMap.optString(key))
                    }
                }
            }
            // 保存取得的值
            urlList.add(work?.optString(0, null))
            methodList.add(method)
            headersList.add(headersBuilder.build())
            mediaTypeList.add(mediaType.toMediaType())
            sendBodyList.add(sendBody)
            charsetList.add(charset)
        }

        // 解析传入的参数
        val actions = JSONArray(inputActions)
        // 统计需要发起的请求数
        val actionCount = actions.length()
        // 存储请求成功的字符串
        val resultsText = arrayOfNulls<String>(actionCount)
        try {
            var actionsStep = actionCount;
            /**
             * 发起异步网络请求
             */
            fun callAsync(request: Request, resIndex: Int, retryCount: Int) {
                client.newCall(request).enqueue(object : Callback {
                    // 请求成功的回调函数
                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 200) {
                            Log.d("OkHttpAsync", "[200] ${response.request.url}?${sendBodyList[resIndex]}")
                            // 识别Charset并解码内容
                            resultsText[resIndex] = response.body?.let { body ->
                                val bodyBytes = body.bytes()
                                var charset = charsetList[resIndex] ?: body.contentType()?.charset()
                                if (charset == null) {
                                    val initText = String(bodyBytes).toLowerCase(Locale.US)
                                    var charIndex = initText.indexOf("charset=")
                                    if (charIndex > 0) {
                                        charIndex += if (initText[charIndex + 8] == '"') 9 else 8
                                        val charEnd = initText.indexOf('"', charIndex)
                                        if (charEnd > 0) charset = Charset.forName(initText.substring(charIndex, charEnd))
                                    }
                                }
                                String(bodyBytes, charset ?: Charsets.UTF_8)
                            } ?: ""
                            --actionsStep
                        } else {
                            Log.d("OkHttpAsync", "[${response.code}] ${request.url}?${sendBodyList[resIndex]}\n${response.message}")
                            val retryAgain = retryCount - 1
                            if (retryAgain > 0) {
                                sleep(100)
                                callAsync(request, resIndex, retryAgain)
                            } else {
                                --actionsStep
                            }
                        }
                    }

                    // 网络错误的回调函数
                    override fun onFailure(call: Call, e: IOException) {
                        printLog("[OkHttpAsync] (${request.url}　|　${sendBodyList[resIndex]}): ${e.stackTraceToString()}")
                        Log.w("OkHttpAsync", "[${e.message}] ${request.url}?${sendBodyList[resIndex]}")
                        val retryAgain = retryCount - 1
                        if (retryAgain > 0) {
                            sleep(100)
                            callAsync(request, resIndex, retryAgain)
                        } else {
                            --actionsStep
                        }
                    }
                })
            }

            // 循环添加网络请求任务
            for (i in 0 until actionCount) {
                readParams(actions.optJSONArray(i))
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
            // 等待网络请求完成(最多等待30s)
            while (actionsStep > 0 && --maxTimeOut > 0) sleep(100)
            return resultsText
        } catch (t: Throwable) {
            t.printStackTrace()
            Log.d("OkHttpAsync", "[ERROR] $t")
            return resultsText
        }
    }

    /**
     * 绑定到 JsBridge 对象
     * @param jsBridge 目标对象名称
     * @param name 注入到js内的名称
     */
    fun binding(js: V8, apiName: String = "fetch") {
        // 注入 okHttp
        V8JavaAdapter.injectObject("GlobalOkHttp", this, js);
        // 包装 js 方法
        val jsAPI = """
             class fetchOnce {
                constructor(url, params) {
                    let FetchRes = JSON.parse(GlobalOkHttp.fetchKt(url, params));
                    this.finalUrl = FetchRes.finalUrl;
                    this.status = FetchRes.status;
                    this.statusText = FetchRes.statusText;
                    this.error = FetchRes.error;
                    this.success = FetchRes.success;
                    this.body = FetchRes.text;
                }
                text() { return this.body; }
                json() { return JSON.parse(this.body); }
            }
            var $apiName = (url, params) => new fetchOnce(url, JSON.stringify(params) || null);
            var ${apiName}All = (fetchArray, retryNum, threadCount) => GlobalOkHttp.fetchAllKt(JSON.stringify(fetchArray), retryNum || null, threadCount || null);
            console.debug('OkHttp 方法已注入为 $apiName');
        """.trimIndent()
        // 注入 js 包装的方法
        js.executeVoidScript(jsAPI)
    }
}