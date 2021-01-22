package com.antecer.nekopaw

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
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
import timber.log.Timber
import java.net.SocketTimeoutException

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope() {
    @SuppressLint("LogNotTimber")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        mBinding.printBox.text = stringFromJNI()


        // 简化Log调用
        val logQJS = { msg: String? -> Log.i("QuickJS", msg ?: "null") }

        logQJS("测试开始")

        val jsBridge = JsBridge(JsBridgeConfig.bareConfig())
        logQJS("JS引擎建立")

        JsValue.fromNativeFunction1(jsBridge, logQJS).assignToGlobal("log")
        logQJS("log函数载入")

        // 输出数据到UI
//        val jsPrint = { msg: String -> mBinding.printBox.post { mBinding.printBox.append("\n$msg") } }
//        JsValue.fromNativeFunction1(jsBridge, jsPrint).assignToGlobal("print")
//        logQJS("print函数载入")

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

        val jsBenchMarks = assets.open("benchmarks.js").readBytes().decodeToString()
        val htmlParser = assets.open("htmlparser.js").readBytes().decodeToString()

        launch {
            val parser: Any = jsBridge.evaluate(htmlParser)
            val jsCode = """
            log('JS Start');
            var response = fetch('https://www.zhaishuyuan.com/search/', {
                method: 'POST',
                headers:{
                    'content-type': 'application/x-www-form-urlencoded',
                    'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.36'
                },
                body: `key=%D0%DE%D5%E6`
		    });
            var html = response.text;
            var doc = HTMLtoDOM(html);
            doc.getElementsByTagName("p").length
        """.trimIndent()
            logQJS(jsBridge.evaluate(jsCode))
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