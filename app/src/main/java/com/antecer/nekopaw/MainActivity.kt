package com.antecer.nekopaw

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.SearchView
import android.widget.TextView
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
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope() {
    lateinit var printBox: TextView

    @SuppressLint("LogNotTimber")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        class CrashReportingTree : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {}
        }
        Timber.plant(if (BuildConfig.DEBUG) DebugTree() else CrashReportingTree())

        // 绑定视图
        val mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        // 设置标题
        mBinding.toolbar.title = "猫爪"
        // 绑定文本框
        printBox = mBinding.printBox
        // 允许内容滚动
        printBox.text = stringFromJNI()
        printBox.movementMethod = ScrollingMovementMethod.getInstance()

        // 绑定搜索事件
        mBinding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(newText: String?): Boolean {
                if (newText != null) {
                    queryActions(newText)
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        // 调用原生方法的示例
        //findViewById<TextView>(R.id.sample_text).text = (stringFromJNI())
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

    fun queryActions(searchKey: String) {

        // 简化Log调用
        val logQJS = { msg: Any -> Timber.tag("JsAPI").d(msg.toString()) }

        logQJS("测试开始")

        val jsBridge = JsBridge(JsBridgeConfig.bareConfig())
        logQJS("JS引擎建立")

        val console = object : JsToNativeInterface {
            fun log(msg: Any?) {
                Timber.tag("QuickJS").d(msg?.toString() ?: "null")
            }
        }
        JsValue.fromNativeObject(jsBridge, console).assignToGlobal("console")
        logQJS("console方法注入")

        // 输出数据到UI
        var startTime: Long = System.currentTimeMillis()
        val jsPrint = { msg: String ->
            printBox.post {
                val fmter = SimpleDateFormat("[mm:ss.SSS]", Locale.getDefault())
                val msgx = "${fmter.format(Date(System.currentTimeMillis() - startTime))} $msg"
                printBox.append("$msgx\n")
                Timber.tag("QuickJS").i(msgx)
            }
        }
        JsValue.fromNativeFunction1(jsBridge, jsPrint).assignToGlobal("print")
        logQJS("print方法注入")

        JsValue.fromNativeFunction2(jsBridge) { s: String, c: String? -> URLEncoder.encode(s, c?:"utf-8") }.assignToGlobal("UrlEncoder")
        logQJS("UrlEncoder方法注入")

        // 模拟fetch请求
        fun fetch(url: String, params: JsonObjectWrapper?): String? {
            Timber.i(url)

            var responseError: String? = null
            var responseCode: Int? = null
            var responseMsg: String? = null
            var responseText: String? = null

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
            return responseText
//            JsonObjectWrapper(
//                "code" to responseCode,
//                "message" to responseMsg,
//                "text" to responseText,
//                "error" to responseError,
//            )
        }
        JsValue.fromNativeFunction2(jsBridge) { url: String, params: JsonObjectWrapper? -> fetch(url, params) }.assignToGlobal("fetch")
        logQJS("fetch方法注入")

        JsoupToJS().binding(jsBridge, "jsoup")
        logQJS("jsoup方法注入")

        printBox.text = ""
        val js = assets.open("zhaishuyuan.js").readBytes().decodeToString()
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
                var arr = new Document('<div><a>1</a><a>2</a><a>3</a></div>');
                arr.querySelector('a').remove();
                console.log(JSON.stringify(arr.queryAllText('a')));
                console.log(JSON.stringify(arr.querySelectorAll('a').text()));
                $('#inner').before('<a>before</a>');
                console.log(dom1.innerHTML());
            """.trimIndent()
            //jsBridge.evaluateAsync<Any>(jsCode).await()
            //jsBridge.evaluateAsync<Any>("jsoup.dispose()").await()

            startTime = System.currentTimeMillis()
            jsPrint("载入JS数据")
            jsBridge.evaluateAsync<Any>(js).await()
            jsPrint("数据载入完成")
            val stepCount: Int = jsBridge.evaluate("parseInt(step.length)")
            jsBridge.evaluateAsync<Any>("step[0]('$searchKey')").await()
            for (index in 1 until stepCount) {
                jsBridge.evaluateAsync<Any>("step[$index]()").await()
            }
            jsPrint("所有任务完成")
        }
    }
}