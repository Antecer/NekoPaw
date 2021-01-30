package com.antecer.nekopaw

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import com.antecer.nekopaw.api.JsEngine
import com.antecer.nekopaw.databinding.ActivityMainBinding
import com.antecer.nekopaw.web.NetworkUtils
import com.antecer.nekopaw.web.WebHttpServer
import com.antecer.nekopaw.web.WebSocketServer
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.IOException

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope() {

    companion object {
        @JvmStatic
        lateinit var INSTANCE: MainActivity
            private set

        @JvmStatic
        lateinit var UI: ActivityMainBinding
            private set

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        INSTANCE = this
        // 绑定视图
        UI = ActivityMainBinding.inflate(layoutInflater)
        setContentView(UI.root)

        // 配置日志输出
        class ReleaseTree : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            }
        }
        Timber.plant(if (BuildConfig.DEBUG) DebugTree() else ReleaseTree())


        // 设置标题
        UI.toolbar.title = "猫爪"
        // 允许内容滚动
        UI.printBox.movementMethod = ScrollingMovementMethod.getInstance()

        // 初始化JS引擎
        //val js = assets.open("zhaishuyuan.js").readBytes().decodeToString()
        val js = assets.open("zwdu.js").readBytes().decodeToString()
        GlobalScope.launch {
            JsEngine.ins.tag("main").setLogOut { msg ->
                UI.printBox.post {
                    UI.printBox.append("$msg\n")
                }
            }
            JsEngine.ins.tag("main").jsBridge.evaluateBlocking<Any>(js)
            Timber.tag("QuickJS").d("载入JS完成")
        }

        // 绑定搜索事件
        UI.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(inputText: String?): Boolean {
                if (inputText != null) {
                    UI.search.clearFocus() // 事件触发后取消焦点,防止事件被多次触发
                    UI.printBox.text = ""  // 清空日志输出
                    queryActions(inputText)
                }
                return false
            }

            override fun onQueryTextChange(inputText: String?): Boolean {
                return false
            }
        })

        // 配置Web服务器
        NetworkUtils.getLocalIPAddress()?.let { address ->
            try {
                // 启动http服务器
                val httpPort = 8888
                WebHttpServer(httpPort).start()
                UI.printBox.append("\n\n启动 webHttpServer\nhttp://${address.hostAddress}:$httpPort")
                // 启动socket服务器
                val socketPort = 8889
                WebSocketServer(socketPort).start(1000 * 30 * 100)
                UI.printBox.append("\n\n启动 webSocketServer\nws://${address.hostAddress}:$socketPort/runJS")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun queryActions(searchKey: String) {
        launch(IO) {
            val jsManager = JsEngine.ins.tag("main")
            try {
                val stepCount: Int = jsManager.jsBridge.evaluate("parseInt(step.length)")
                jsManager.jsBridge.evaluateAsync<Any>("step[0]('$searchKey')").await()
                for (index in 1 until stepCount) {
                    jsManager.jsBridge.evaluateAsync<Any>("step[$index]()").await()
                }
            } catch (err: Exception) {
                jsManager.jsBridge.evaluateNoRetVal("console.error(${err.stackTraceToString()})")
            }
            Timber.tag("QuickJS").d("JS任务完成")
            jsManager.disposeJsoup()    // 释放jsoup占用的资源
        }
    }
}