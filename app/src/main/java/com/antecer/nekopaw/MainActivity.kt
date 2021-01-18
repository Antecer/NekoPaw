package com.antecer.nekopaw

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.koushikdutta.quack.QuackContext
import de.prosiebensat1digital.oasisjsbridge.JsBridge
import de.prosiebensat1digital.oasisjsbridge.JsBridgeConfig
import de.prosiebensat1digital.oasisjsbridge.JsValue

class MainActivity : AppCompatActivity() {
    class Foo{
        fun print(s: Any) = println(s)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val js = assets.open("bechmarks.js").readBytes().decodeToString()
        val quack = QuackContext.create()
        quack.globalObject.set("Foo",Foo::class.java)
        println(quack.evaluate("print = (...args) => new Foo().print([...args]);"))
        println("begin quack")
        quack.evaluate(js)
        println("end quack")
        val jsBridge = JsBridge(JsBridgeConfig.bareConfig())
        JsValue.fromNativeFunction1(jsBridge) { s:Any -> println(s) }.assignToGlobal("print")
        println("begin JsBridge")
        jsBridge.evaluateNoRetVal(js)
        println("end JsBridge")
        // 调用原生方法的示例
        findViewById<TextView>(R.id.sample_text).text = (stringFromJNI())
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