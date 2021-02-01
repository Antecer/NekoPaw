package com.antecer.nekopaw.api

import com.eclipsesource.v8.V8
import io.alicorn.v8.V8JavaAdapter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/**
 * 连接 Jsoup 和 QuickJS(JsBridge)
 */
@Suppress("unused")
class JsoupToJS private constructor() {
    companion object {
        val ins: JsoupToJS by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            JsoupToJS()
        }
    }

    // 存储多个 JpManager 给不同线程调用
    private val jsoupMap = mutableMapOf<String, JpManager>()

    /**
     * 调度或新建指定Tag的 JpManager
     */
    fun tag(t: String): JpManager {
        return jsoupMap.getOrPut(t) { JpManager() }
    }

    /**
     * 移除指定Tag的JsBridge并释放被占用的资源
     */
    fun remove(t: String) {
        jsoupMap[t]?.let {
            it.dispose()
            jsoupMap.remove(t)
        }
    }

    /**
     * 包装 jsoup 方法
     */
    class JpManager {
        // 创建Element容器
        private val aMap = mutableMapOf<String, Document>()
        private val bMap = mutableMapOf<String, Element>()
        private val cMap = mutableMapOf<String, Elements>()

        /**
         * 释放 jsoup 占用的资源
         */
        fun dispose() {
            aMap.clear()
            bMap.clear()
            cMap.clear()
        }

        fun parse(html: String): String {
            val key = "a${aMap.size}"
            aMap[key] = Jsoup.parse(html)
            return key
        }

        fun querySelector(trait: String, mark: String): String {
            val key = "b${bMap.size}"
            when (mark[0]) {
                'a' -> bMap[key] = aMap[mark]!!.selectFirst(trait)
                'b' -> bMap[key] = bMap[mark]!!.selectFirst(trait)
                else -> return "null"
            }
            return key
        }

        fun querySelectorAll(trait: String, mark: String): String {
            val key = "c${cMap.size}"
            when (mark[0]) {
                'a' -> cMap[key] = aMap[mark]!!.select(trait)
                'b' -> cMap[key] = bMap[mark]!!.select(trait)
                'c' -> cMap[key] = cMap[mark]!!.select(trait)
                else -> return "null"
            }
            return key
        }

        fun getElementById(trait: String, mark: String): String {
            val key = "b${bMap.size}"
            when (mark[0]) {
                'a' -> bMap[key] = aMap[mark]!!.getElementById(trait)
                'b' -> bMap[key] = bMap[mark]!!.getElementById(trait)
                else -> return "null"
            }
            return key
        }

        fun getElementByClass(trait: String, mark: String): String {
            val key = "b${cMap.size}"
            when (mark[0]) {
                'a' -> cMap[key] = aMap[mark]!!.getElementsByClass(trait)
                'b' -> cMap[key] = bMap[mark]!!.getElementsByClass(trait)
                else -> return "null"
            }
            return key
        }

        fun getElementByTag(trait: String, mark: String): String {
            val key = "b${cMap.size}"
            when (mark[0]) {
                'a' -> cMap[key] = aMap[mark]!!.getElementsByTag(trait)
                'b' -> cMap[key] = bMap[mark]!!.getElementsByTag(trait)
                else -> return "null"
            }
            return key
        }

        fun outerHtml(mark: String): String {
            return when (mark[0]) {
                'a' -> aMap[mark]!!.outerHtml()
                'b' -> bMap[mark]!!.outerHtml()
                'c' -> cMap[mark]!!.outerHtml()
                else -> ""
            }
        }

        fun innerHTML(mark: String, html: String?): String {
            return if (html == null) {
                when (mark[0]) {
                    'a' -> aMap[mark]!!.html()
                    'b' -> bMap[mark]!!.html()
                    'c' -> cMap[mark]!!.html()
                    else -> ""
                }
            } else {
                when (mark[0]) {
                    'a' -> aMap[mark]!!.html(html)
                    'b' -> bMap[mark]!!.html(html)
                    'c' -> cMap[mark]!!.html(html)
                }
                html
            }
        }

        fun innerText(mark: String, text: String?): String {
            return when (mark[0]) {
                'a' -> aMap[mark]!!.text()
                'b' -> {
                    if (text != null) {
                        bMap[mark]!!.text(text); text
                    } else {
                        bMap[mark]!!.text()
                    }
                }
                'c' -> cMap[mark]!!.text()
                else -> ""
            }
        }

        // jsoup自有方法
        fun textNodes(mark: String): Array<String> {
            var textNodes: MutableList<TextNode>? = null
            when (mark[0]) {
                'a' -> textNodes = aMap[mark]!!.textNodes()
                'b' -> textNodes = bMap[mark]!!.textNodes()
                'c' -> textNodes = cMap[mark]!!.textNodes()
            }
            return textNodes?.map { T -> T.text() }?.toTypedArray() ?: emptyArray()
        }

        fun remove(mark: String) {
            when (mark[0]) {
                'a' -> aMap[mark]?.remove()
                'b' -> bMap[mark]?.remove()
                'c' -> cMap[mark]?.remove()
            }
        }

        fun before(mark: String, html: String) {
            when (mark[0]) {
                'a' -> aMap[mark]?.before(html)
                'b' -> bMap[mark]?.before(html)
                'c' -> cMap[mark]?.before(html)
            }
        }

        // 自定义方法
        fun queryText(trait: String, mark: String): String {
            return when (mark[0]) {
                'a' -> aMap[mark]!!.selectFirst(trait).text()
                'b' -> bMap[mark]!!.selectFirst(trait).text()
                else -> ""
            }
        }

        fun queryAllText(trait: String, mark: String): Array<String> {
            val arrText: ArrayList<String> = ArrayList()
            when (mark[0]) {
                'a' -> for (item in aMap[mark]!!.select(trait)) arrText.add(item.text())
                'b' -> for (item in bMap[mark]!!.select(trait)) arrText.add(item.text())
                'c' -> for (item in cMap[mark]!!.select(trait)) arrText.add(item.text())
            }
            return arrText.toTypedArray()
        }

        fun queryAttr(trait: String, attr: String, mark: String): String {
            return when (mark[0]) {
                'a' -> aMap[mark]!!.selectFirst(trait).attr(attr)
                'b' -> bMap[mark]!!.selectFirst(trait).attr(attr)
                else -> ""
            }
        }

        fun queryAllAttr(trait: String, attr: String, mark: String): Array<String> {
            val arrAttr: ArrayList<String> = ArrayList()
            when (mark[0]) {
                'a' -> for (item in aMap[mark]!!.select(trait)) arrAttr.add(item.attr(attr))
                'b' -> for (item in bMap[mark]!!.select(trait)) arrAttr.add(item.attr(attr))
                'c' -> for (item in cMap[mark]!!.select(trait)) arrAttr.add(item.attr(attr))
            }
            return arrAttr.toTypedArray()
        }

        fun queryRemove(base: String, trait: String) {
            aMap[base]?.select(trait)?.remove()
        }

        fun queryBefore(base: String, trait: String, html: String) {
            aMap[base]?.select(trait)?.before(html)
        }

        /**
         * 绑定到 JsBridge 对象
         * @param jsBridge 目标对象名称
         * @param name 注入到js内的名称
         */
        fun binding(js: V8, apiName: String = "Document") {
            // 注入 原装的Jsoup
            V8JavaAdapter.injectClass(Jsoup::class.java, js)
            // 注入 封装的Jsoup
            V8JavaAdapter.injectObject("GlobalJsoup", this, js);
            // 包装 js 方法
            val jsAPI = """
            class $apiName {
                #mark;
                constructor(html, mark) { this.#mark = html ? GlobalJsoup.parse(html) : mark; }
                querySelector(trait) { return new Document(null, GlobalJsoup.querySelector(trait, this.#mark)); }
                querySelectorAll(trait) { return new Document(null, GlobalJsoup.querySelectorAll(trait, this.#mark)); }
                getElementById(trait) { return new Document(null, GlobalJsoup.getElementById(trait, this.#mark)); }
                getElementByTag(trait) { return new Document(null, GlobalJsoup.getElementByTag(trait, this.#mark)); }
                getElementByClass(trait) { return new Document(null, GlobalJsoup.getElementByClass(trait, this.#mark)); }
                outerHTML() { return GlobalJsoup.outerHtml(this.#mark); }
                innerHTML(html) { return GlobalJsoup.innerHTML(this.#mark, html||null); }
                innerText(text) { return GlobalJsoup.innerText(this.#mark, text||null); }
            
                // jsoup自有方法
                selectFirst(trait) { return new Document(null, GlobalJsoup.querySelector(trait, this.#mark)); }
                select(trait) { return new Document(null, GlobalJsoup.querySelectorAll(trait, this.#mark)); }
                html(s) { return GlobalJsoup.innerHTML(this.#mark, s||null); }
                text(s) { return GlobalJsoup.innerText(this.#mark, s||null); }
                textNodes() { return GlobalJsoup.textNodes(this.#mark); }
                remove() { GlobalJsoup.remove(this.#mark); }
                before(html) { GlobalJsoup.before(this.#mark, html); }
                
                // 自定义方法
                queryRemove(trait) { GlobalJsoup.queryRemove(this.#mark, trait) }
                queryBefore(trait, html) { GlobalJsoup.queryRemove(this.#mark, trait, html) }
                queryText(trait) { return GlobalJsoup.queryText(trait, this.#mark); }
                queryAllText(trait) { return GlobalJsoup.queryAllText(trait, this.#mark); }
                queryAttr(trait, attr) { return GlobalJsoup.queryAttr(trait, attr, this.#mark); }
                queryAllAttr(trait, attr) { return GlobalJsoup.queryAllAttr(trait, attr, this.#mark); }
                dispose() { GlobalJsoup.dispose(); }
            }
            console.debug('Jsoup 方法已注入为 $apiName');
        """.trimIndent()
            // 注入 js 包装的方法
            js.executeVoidScript(jsAPI)
        }
    }
}