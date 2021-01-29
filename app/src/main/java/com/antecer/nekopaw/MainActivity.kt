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
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.IOException

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope() {

    companion object {
        @JvmStatic
        lateinit var INSTANCE: MainActivity
            private set

        init {
            // 用于在应用程序启动时加载"native-lib"库。
            System.loadLibrary("native-lib")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        INSTANCE = this

        // 配置日志输出
        class ReleaseTree : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            }
        }
        Timber.plant(if (BuildConfig.DEBUG) DebugTree() else ReleaseTree())

        // 绑定视图
        val mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        // 设置标题
        mBinding.toolbar.title = "猫爪"
        // 调用原生方法的示例
        mBinding.printBox.text = stringFromJNI()
        // 允许内容滚动
        mBinding.printBox.movementMethod = ScrollingMovementMethod.getInstance()

        // 初始化JS引擎
        //val js = assets.open("zhaishuyuan.js").readBytes().decodeToString()
        val js = assets.open("zwdu.js").readBytes().decodeToString()
        GlobalScope.launch {
            JsEngine.instance.setLogout(mBinding.printBox)
            JsEngine.instance.jsBridge.evaluateBlocking<Any>(js)
            Timber.tag("QuickJS").d("载入JS完成")
        }

        // 绑定搜索事件
        mBinding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(inputText: String?): Boolean {
                if (inputText != null) {
                    mBinding.search.clearFocus() // 事件触发后取消焦点,防止事件被多次触发
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
                // 启动socket服务器
                WebSocketServer(52345).start(1000 * 30 * 100)
                mBinding.printBox.append("\n\n启动 webSocketServer\nws://${address.hostAddress}:52345/runJS")
            } catch (e: IOException) {
                e.printStackTrace()
            }
            // 启动http服务器
            WebHttpServer(58888).start()
            mBinding.printBox.append("\n\n启动 webHttpServer\nhttp://${address.hostAddress}:58888")
        }

    }

    /**
     * 由"native-lib"原生库实现的方法,该库随此应用程序一起打包
     */
    external fun stringFromJNI(): String

    fun queryActions(searchKey: String) {
        launch {
            try {
                JsEngine.instance.clearLogView()
                val stepCount: Int = JsEngine.instance.jsBridge.evaluate("parseInt(step.length)")
                JsEngine.instance.clearTimer()
                JsEngine.instance.jsBridge.evaluateAsync<Any>("step[0]('$searchKey')").await()
                for (index in 1 until stepCount) {
                    JsEngine.instance.jsBridge.evaluateAsync<Any>("step[$index]()").await()
                }
                Timber.tag("QuickJS").d("JS任务完成")
                JsEngine.instance.jsBridge.evaluateNoRetVal("GlobalJsoup.dispose()") // 释放jsoup资源

            } catch (err: Exception) {
                JsEngine.instance.jsBridge.evaluateNoRetVal("console.error(${err.stackTraceToString()})")
            }
        }
    }
}