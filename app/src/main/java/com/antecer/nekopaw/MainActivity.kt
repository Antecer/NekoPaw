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
        val logQJS = { msg: Any? -> Log.i("QuickJS", msg?.toString() ?: "null") }

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

        val document = object:JsToNativeInterface {
            val documentList = mutableListOf<Document>()
            val elementsList = mutableListOf<Elements>()
            val elementList = mutableListOf<Element>()

            fun parse(html:String):JsonObjectWrapper {
                documentList.add(Jsoup.parse(html))
                return JsonObjectWrapper(
                    "address" to documentList.size - 1,
                    "type" to "document"
                )
            }

            fun query(typeAddress:JsonObjectWrapper, query: String): JsonObjectWrapper {
                val o = typeAddress.toPayloadObject() ?: return JsonObjectWrapper.Undefined
                val address = o.getInt("address") ?: return JsonObjectWrapper.Undefined
                val elements = when(o.getString("type")){
                    "document" -> documentList[address].select(query)
                    "element" -> elementList[address].select(query)
                    "elements" -> elementsList[address].select(query)
                    else -> Elements()
                }
                elementsList.add(elements)
                return JsonObjectWrapper(
                    "address" to elementsList.size - 1,
                    "type" to "elements"
                )
            }

            fun getElementById(typeAddress:JsonObjectWrapper, id: String): JsonObjectWrapper {
                val o = typeAddress.toPayloadObject() ?: return JsonObjectWrapper.Undefined
                val address = o.getInt("address") ?: return JsonObjectWrapper.Undefined
                val element = when(o.getString("type")){
                    "document" -> documentList[address].getElementById(id)
                    "element" -> elementList[address].getElementById(id)
                    else -> Element("html")
                }
                elementList.add(element)
                return JsonObjectWrapper(
                    "address" to elementList.size - 1,
                    "type" to "element"
                )
            }

            fun text(typeAddress:JsonObjectWrapper):String? {
                val o = typeAddress.toPayloadObject() ?: return null
                val address = o.getInt("address") ?: return null
                return when(o.getString("type")){
                    "document" -> documentList[address].text()
                    "element" -> elementList[address].text()
                    "elements" -> elementsList[address].text()
                    else -> null
                }
            }
            fun dispose(){
                documentList.clear()
                elementList.clear()
                elementsList.clear()
            }
        }

        JsValue.fromNativeObject(jsBridge, document).assignToGlobal("native_dom")
        launch {
            val jsCode = """
                class Document{
                    constructor(typeAddress){
                        this.typeAddress = typeAddress;
                    }
                    text = function(){
                        return native_dom.text(this.typeAddress)
                    }
                    getElementById = function(id){
                        return new Document(native_dom.getElementById(this.typeAddress, id))
                    }
                    dispose = () => native_dom.dispose()
                }
            parse = (html) => new Document(native_dom.parse(html))
            var dom1 = parse('<div id="outer">outer content<p id="inner">inner content</p></div>')
                            .getElementById("outer")
                            .getElementById("inner");
            var dom2 = parse("<p>546</p>");
            log(dom1.text());
            log(dom2.text());
            new Document().dispose()
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