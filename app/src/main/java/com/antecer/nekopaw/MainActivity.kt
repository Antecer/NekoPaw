package com.antecer.nekopaw

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.BuildConfig
import com.antecer.nekopaw.api.JsEngine
import com.antecer.nekopaw.databinding.ActivityMainBinding
import com.antecer.nekopaw.web.NetworkUtils
import com.antecer.nekopaw.web.WebSocketServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.IOException
import java.lang.Exception


class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 配置日志输出
        class CrashReportingTree : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {}
        }
        Timber.plant(if (BuildConfig.DEBUG) DebugTree() else CrashReportingTree())

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
        val js = assets.open("zhaishuyuan.js").readBytes().decodeToString()
        GlobalScope.launch {
            JsEngine.instance.setLogout(mBinding.printBox)
            JsEngine.instance.jsBridge.evaluateBlocking<Any>(js)
            Timber.tag("QuickJS").d("载入JS完成")
        }

        // 配置WebSocket
        NetworkUtils.getLocalIPAddress()?.let { address ->
            try {
                WebSocketServer(52345).start(1000 * 30 * 100)
                mBinding.printBox.append("\n\n启动 webSocketServer\nws://${address.hostAddress}:52345/runJS")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // 绑定搜索事件
        mBinding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(inputText: String?): Boolean {
                if (inputText != null) {
                    queryActions(inputText)
                }
                return false
            }

            override fun onQueryTextChange(inputText: String?): Boolean {
                return false
            }
        })
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
        launch {
            try {
                val jsEngine = JsEngine.instance
                jsEngine.clearLogView()
                val stepCount: Int = jsEngine.jsBridge.evaluate("parseInt(step.length)")
                jsEngine.clearTimer()
                jsEngine.jsBridge.evaluateAsync<Any>("step[0]('$searchKey')").await()
                for (index in 1 until stepCount) {
                    jsEngine.jsBridge.evaluateAsync<Any>("step[$index]()").await()
                }
                Timber.tag("QuickJS").d("JS任务完成")
            } catch (err: Exception) {
                err.printStackTrace()
                Timber.tag("QuickJS").e(err)
            }
        }
    }
}