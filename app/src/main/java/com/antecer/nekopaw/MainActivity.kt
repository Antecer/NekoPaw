package com.antecer.nekopaw

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import de.prosiebensat1digital.oasisjsbridge.JsBridge
import de.prosiebensat1digital.oasisjsbridge.JsBridgeConfig
import de.prosiebensat1digital.oasisjsbridge.JsToNativeInterface
import de.prosiebensat1digital.oasisjsbridge.JsValue
import kotlinx.coroutines.*


class MainActivity : Activity(),
    CoroutineScope by MainScope() {

    @ExperimentalCoroutinesApi
    @SuppressLint("LogNotTimber")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)

        launch {
            val js = assets.open("bechmarks.js").readBytes().decodeToString()


            Log.i("JS", "-------------begin JsBridge")

            val result = "12345789"
            val jsBridge = JsBridge(JsBridgeConfig.bareConfig())
            // 测试1
            JsValue.fromNativeValue(jsBridge, result).assignToGlobal("result")
            JsValue.fromNativeFunction1(jsBridge) { s: Any -> Log.i("JS", s.toString()) }
                .assignToGlobal("print")
            JsValue.fromNativeFunction1(jsBridge) { s: String -> (s + "1") }
                .assignToGlobal("test")
            Log.i("JS", "-------------mid JsBridge")

            jsBridge.evaluateNoRetVal("print(test('s'))")
            jsBridge.evaluateNoRetVal("print(result)")
            val msg: String = jsBridge.evaluate("result")
            Log.i("JS", "-----JsBridge Get: $msg")

//            val sum: Int = jsBridge.evaluate("1+2")
//            Log.i("JsBridgeTest", sum.toString())
//            Log.i("JsBridgePrint", "-------------begin JsBridge")
            Log.i("JS", "-------------end JsBridge")




            val obj = object : JsToNativeInterface {
                fun method(): Double {
                    return 0.01
                }
            }
            // Create a JS proxy to the native object
            val nativeApi: JsValue = JsValue.fromNativeObject(jsBridge, obj)
            JsValue.fromNativeObject(jsBridge, obj).assignToGlobal("natAPI")
            jsBridge.evaluateNoRetVal("print(natAPI.method())")
            // Call native method from JS
            //jsBridge.evaluateNoRetVal("globalThis.x = $nativeApi.method();")
        }

        // 调用原生方法的示例
        //findViewById<TextView>(R.id.sample_text).text = (stringFromJNI())
    }

    /**
     * 由“native-lib”原生库实现的方法，该库随此应用程序一起打包。
     */
    external fun stringFromJNI(): String

    companion object {
        // 用于在应用程序启动时加载“native-lib”库。
        init {
            System.loadLibrary("native-lib")
        }
    }

}
