package com.antecer.nekopaw

import android.os.Bundle
import android.os.SystemClock.sleep
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import com.antecer.nekopaw.api.JsEngine
import com.antecer.nekopaw.databinding.ActivityMainBinding
import com.antecer.nekopaw.web.NetworkUtils
import com.antecer.nekopaw.web.WebHttpServer
import com.antecer.nekopaw.web.WebSocketServer
import com.eclipsesource.v8.JavaVoidCallback
import com.eclipsesource.v8.Releasable
import com.eclipsesource.v8.V8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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

        // 设置标题
        UI.toolbar.title = "猫爪"
        // 允许内容滚动
        UI.printBox.movementMethod = ScrollingMovementMethod.getInstance()

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
            // 初始化JS引擎
            val token = System.currentTimeMillis().toString()
            val jsManager = JsEngine.ins.tag(token)
            try {
                // 设置日志输出回调
                jsManager.setLogOut { msg ->
                    UI.printBox.post {
                        UI.printBox.append("$msg\n")
                    }
                }
                // 加载数据源
                val jsSrc = assets.open("zhaishuyuan.js").readBytes().decodeToString()
                //val jsSrc = assets.open("zwdu.js").readBytes().decodeToString()
                jsManager.js.executeVoidScript(jsSrc)
                Log.d("JsManager", "载入JS资源完成")
                // 读取操作步骤
                val stepCount: Int = jsManager.js.executeIntegerScript("parseInt(step.length)")
                jsManager.js.executeVoidScript("step[0](`$searchKey`)")
                for (index in 1 until stepCount) {
                    jsManager.js.executeVoidScript("step[$index]()")
                }
            } catch (err: Exception) {
                val errMsg = err.stackTraceToString()
                jsManager.js.executeVoidScript("""console.error(`$errMsg`);""")
            }
            Log.d("JsManager", "JS任务完成")
            JsEngine.ins.remove(token)
        }
    }
}