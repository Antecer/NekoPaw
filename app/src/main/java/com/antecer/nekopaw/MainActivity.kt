package com.antecer.nekopaw

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 调用原生方法的示例
        findViewById<TextView>(R.id.sample_text).text = stringFromJNI()
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