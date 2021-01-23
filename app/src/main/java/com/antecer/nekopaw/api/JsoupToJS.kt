package com.antecer.nekopaw.api

import de.prosiebensat1digital.oasisjsbridge.JsBridge
import de.prosiebensat1digital.oasisjsbridge.JsToNativeInterface
import de.prosiebensat1digital.oasisjsbridge.JsValue
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * 连接Jsoup和QuickJS(JsBridge)
 */
class JsoupToJS {
    /**
     * 绑定到JsBridge对象
     * @param jsBridge 目标对象名称
     * @param name 注入到js内的名称
     */
    fun binding(jsBridge: JsBridge, name: String = "jsoup") {
        val jsoupKtApi = object : JsToNativeInterface {
            val aMap = mutableMapOf<String, Document>()
            val bMap = mutableMapOf<String, Element>()
            val cMap = mutableMapOf<String, Elements>()

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
                aMap[base]!!.select(trait).remove()
            }

            fun queryBefore(base: String, trait: String, html: String) {
                aMap[base]!!.select(trait).before(html)
            }

            // 释放占用的资源
            fun dispose() {
                aMap.clear()
                bMap.clear()
                cMap.clear()
            }
        }
        JsValue.fromNativeObject(jsBridge, jsoupKtApi).assignToGlobal(name)

        val jsoupAPI = """
class Document {
	#mark;
	constructor(html, mark) { this.#mark = html ? jsoup.parse(html) : mark; }
	querySelector(trait) { return new Document(null, jsoup.querySelector(trait, this.#mark)); }
	querySelectorAll(trait) { return new Document(null, jsoup.querySelectorAll(trait, this.#mark)); }
	getElementById(trait) { return new Document(null, jsoup.getElementById(trait, this.#mark)); }
	getElementByTag(trait) { return new Document(null, jsoup.getElementByTag(trait, this.#mark)); }
	getElementByClass(trait) { return new Document(null, jsoup.getElementByClass(trait, this.#mark)); }
	outerHTML() { return jsoup.outerHtml(this.#mark); }
	innerHTML(html) { return jsoup.innerHTML(this.#mark, html||null); }
	innerText(text) { return jsoup.innerText(this.#mark, text||null); }

    // jsoup自有方法
    selectFirst(trait) { return new Document(null, jsoup.querySelector(trait, this.#mark)); }
    select(trait) { return new Document(null, jsoup.querySelectorAll(trait, this.#mark)); }
    html(s) { return jsoup.innerHTML(this.#mark, s||null); }
    text(s) { return jsoup.innerText(this.#mark, s||null); }
    remove() { jsoup.remove(this.#mark); }
    before(html) { jsoup.before(this.#mark, html); }
    
    // 自定义方法
    queryRemove(trait) { jsoup.queryRemove(this.#mark, trait) }
    queryBefore(trait, html) { jsoup.queryRemove(this.#mark, trait, html) }
    queryText(trait) { return jsoup.queryText(trait, this.#mark); }
    queryAllText(trait) { return jsoup.queryAllText(trait, this.#mark); }
    queryAttr(trait, attr) { return jsoup.queryAttr(trait, attr, this.#mark); }
    queryAllAttr(trait, attr) { return jsoup.queryAllAttr(trait, attr, this.#mark); }
}
            """.trimIndent()
        runBlocking {
            jsBridge.evaluateAsync<Any>(jsoupAPI).await()
        }
    }
}