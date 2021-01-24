package com.antecer.nekopaw

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.SearchView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.antecer.nekopaw.api.JsoupToJS
import com.antecer.nekopaw.api.OkHttpToJS
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

        JsValue.fromNativeFunction2(jsBridge) { s: String, c: String? -> URLEncoder.encode(s, c ?: "utf-8") }.assignToGlobal("UrlEncoder")
        logQJS("UrlEncoder方法注入")

        printBox.text = ""
        val js = assets.open("zhaishuyuan.js").readBytes().decodeToString()
        launch {
            try {
                OkHttpToJS().binding(jsBridge, "fetch")
                logQJS("OkHttp方法注入为fetch")
                JsoupToJS().binding(jsBridge, "jsoup")
                logQJS("jsoup方法注入")
                startTime = System.currentTimeMillis()
                jsPrint("载入JS数据")
                jsBridge.evaluateAsync<Any>(js).await()
                jsPrint("数据载入完成")
                val stepCount: Int = jsBridge.evaluate("parseInt(step.length)")
                jsBridge.evaluateAsync<Any>("step[0]('$searchKey')").await()
                for (index in 1 until stepCount) {
                    jsPrint("")
                    jsPrint("")
                    jsBridge.evaluateAsync<Any>("step[$index]()").await()
                }
                jsPrint("所有任务完成")
            } catch (err: Exception) {
                err.printStackTrace()
                Timber.tag("QuickJS").e("[ERROR] $err")
                jsPrint("[ERROR] $err")
            }
        }
    }
}