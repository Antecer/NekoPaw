package com.antecer.nekopaw

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.antecer.nekopaw.api.JsoupToJS
import com.antecer.nekopaw.databinding.ActivityMainBinding
import de.prosiebensat1digital.oasisjsbridge.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import timber.log.Timber
import java.net.SocketTimeoutException
import kotlin.math.log

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope() {
    @SuppressLint("LogNotTimber")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        mBinding.printBox.text = stringFromJNI()


        // 简化Log调用
        val logQJS = { msg: Any? -> Log.i("JsAPI", msg?.toString() ?: "null") }

        logQJS("测试开始")

        val jsBridge = JsBridge(JsBridgeConfig.bareConfig())
        logQJS("JS引擎建立")

        val console = object : JsToNativeInterface {
            fun log(msg: Any?) {
                Log.i("QuickJS", msg?.toString() ?: "null")
            }
        }
        JsValue.fromNativeObject(jsBridge, console).assignToGlobal("console")
        logQJS("console方法注入")

        JsValue.fromNativeFunction1(jsBridge, logQJS).assignToGlobal("log")
        logQJS("log函数载入")

        // 输出数据到UI
        val jsPrint = { msg: String -> mBinding.printBox.post { mBinding.printBox.append("\n$msg") } }
        JsValue.fromNativeFunction1(jsBridge, jsPrint).assignToGlobal("print")
        logQJS("print函数载入")

        // 模拟fetch请求
        fun fetch(url: String, params: JsonObjectWrapper?): JsonObjectWrapper {
            Timber.tag("okHttp").i(url)

            var responseError: String? = null
            var responseCode: Int? = null
            var responseMsg: String? = null
            var responseText: String? = null

            var request = Request.Builder().url(url)
            try {
                val paramMap = params?.toPayloadObject()
                // 设置 user-agent
                val userAgent = paramMap?.getObject("headers")?.getString("user-agent")
                if (userAgent != null) request =
                    request.removeHeader("User-Agent").addHeader("User-Agent", userAgent)
                // 设置 Referer
                val referer = paramMap?.getString("Referer")
                if (referer != null) request = request.addHeader("Referer", referer)
                // 设置 content-type
                val mediaType = MediaType.parse(
                    paramMap?.getObject("headers")?.getString("content-type")
                        ?: "application/json;charset=UTF-8"
                )
                // 设置 body
                val requestBody = RequestBody.create(mediaType, paramMap?.getString("body") ?: "");
                // 设置 method(请求模式)
                val method = paramMap?.getString("method") ?: "GET"
                request = if (method == "GET") request.get() else request.post(requestBody)
                // 发送请求
                val response = OkHttpClient().newCall(request.build()).execute()

                responseCode = response.code()
                responseMsg = response.message()
                responseText = response.body()?.string()

                Timber.tag("okHttp").i("Successfully fetched response (query: $url)")
                Timber.tag("okHttp").i("-> responseCode = $responseCode")
                Timber.tag("okHttp").i("-> responseMsg = $responseMsg")
            } catch (e: SocketTimeoutException) {
                Timber.tag("okHttp").i("XHR timeout ($url): $e")
                responseError = "timeout"
            } catch (t: Throwable) {
                Timber.tag("okHttp").i("XHR error ($url): $t")
                responseError = t.message ?: "unknown XHR error"
            }
            return JsonObjectWrapper(
                "code" to responseCode,
                "message" to responseMsg,
                "text" to responseText,
                "error" to responseError,
            )
        }
        JsValue.fromNativeFunction2(jsBridge) { url: String, params: JsonObjectWrapper? -> fetch(url, params) }.assignToGlobal("fetch")
        logQJS("fetch函数载入")

        JsoupToJS().binding(jsBridge, "jsoup")
        logQJS("jsoup载入")

        launch {
            logQJS("jsoup包装")
            val jsCode = """
                var dom1 = new Document('<div id="outer">outer content<p id="inner">inner content</p></div>');
                var d1 = dom1.getElementById("outer").querySelector("#inner").outerHTML();
                var d2 = dom1.outerHTML();
                console.log(d1)
                console.log(d2)
                var dom2 = new Document('<p>546</p>');
                console.log(dom2.text());
                var $ = (s)=> dom1.querySelector(s);
                console.log($('#outer').innerHTML());
                console.log(dom1.queryText('#outer p'))
            """.trimIndent()
            jsBridge.evaluateAsync<Any>(jsCode).await()
            jsBridge.evaluateNoRetVal("jsoup.dispose()")
            logQJS("测试结束")

            // 调用原生方法的示例
            //findViewById<TextView>(R.id.sample_text).text = (stringFromJNI())
        }
    }

    /**
     * 由"native-lib"原生库实现的方法,该库随此应用程序一起打包
     */
    external fun stringFromJNI(): String

    companion object {
        // 用于在应用程序启动时加载"native-lib"库。
        init {
            System.loadLibrary("native-lib")
        }
    }
}