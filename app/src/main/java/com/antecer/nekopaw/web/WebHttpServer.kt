package com.antecer.nekopaw.web

import android.content.res.AssetManager
import com.antecer.nekopaw.MainActivity
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException
import java.util.*

class WebHttpServer(port: Int) : NanoHTTPD(port) {
    private val assetManager: AssetManager = MainActivity.INSTANCE.assets
    private val root = "www"

    // 识别请求的文件类型
    private val getMimeType = { path: String ->
        when (path.split('.').last().toLowerCase(Locale.US)) {
            "html" -> "text/html"
            "js" -> "text/javascript"
            "json" -> "application/json"
            "css" -> "text/css"
            "jpeg" -> "image/jpeg"
            "jpg" -> "image/jpeg"
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "gif" -> "image/gif"
            else -> "text/html"
        }
    }

    // 响应浏览器发起的请求
    override fun serve(session: IHTTPSession): Response? {
        var response: Response? = null
        val pathname = session.uri
        try {
            when (session.method.name) {
                "OPTIONS" -> {
                    response = newFixedLengthResponse("")
                    response.addHeader("Access-Control-Allow-Methods", "POST")
                    response.addHeader("Access-Control-Allow-Headers", "content-type")
                    response.addHeader("Access-Control-Allow-Origin", session.headers["origin"])
                    return response
                }
                "POST" -> {
                    val files = HashMap<String, String>()
                    session.parseBody(files)
                    val postData = files["postData"]

//                    when (pathname) {
//                        "/saveSource" -> returnData = SourceController.saveSource(postData)
//                        "/saveSources" -> returnData = SourceController.saveSources(postData)
//                        "/saveBook" -> returnData = BookshelfController.saveBook(postData)
//                        "/deleteSources" -> returnData = SourceController.deleteSources(postData)
//                    }
                }
                "GET" -> {
                    val parameters = session.parameters
//                    when (pathname) {
//                        "/getSource" -> returnData = SourceController.getSource(parameters)
//                        "/getSources" -> returnData = SourceController.sources
//                        "/getBookshelf" -> returnData = BookshelfController.bookshelf
//                        "/getChapterList" ->
//                            returnData = BookshelfController.getChapterList(parameters)
//                        "/getBookContent" ->
//                            returnData = BookshelfController.getBookContent(parameters)
//                    }
                }
            }
        } catch (e: Exception) {
            return newFixedLengthResponse(e.stackTraceToString())
        }

        return try {
            var filePath = if (pathname.last() == '/') pathname + "index.html" else pathname
            filePath = (root + filePath).replace("/+".toRegex(), File.separator)
            val inputStream = assetManager.open(filePath)
            newChunkedResponse(Response.Status.OK, getMimeType(filePath), inputStream)
        } catch (e: IOException) {
            newFixedLengthResponse(e.stackTraceToString())
        }
    }
}