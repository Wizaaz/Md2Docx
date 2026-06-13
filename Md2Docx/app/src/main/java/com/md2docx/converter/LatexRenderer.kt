package com.md2docx.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * 使用 WebView + 本地 KaTeX 将 LaTeX 渲染为 Bitmap
 * 完全离线可用，无需网络
 */
class LatexRenderer(private val context: Context) {

    private val density = context.resources.displayMetrics.density

    /** 批量渲染 LaTeX 表达式，返回 LaTeX → Bitmap 映射 */
    suspend fun render(latexExpressions: List<String>): Map<String, Bitmap> {
        if (latexExpressions.isEmpty()) return emptyMap()

        // 去重，避免重复渲染
        val unique = latexExpressions.distinct()
        val results = ConcurrentHashMap<String, Bitmap>()

        withContext(Dispatchers.Main) {
            val webView = createWebView()

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // 页面加载完毕后开始逐个渲染
                    view?.post {
                        renderAllSequentially(webView, unique, results)
                    }
                }
            }

            // 加载本地 HTML 模板
            webView.loadUrl("file:///android_asset/latex_render.html")
        }

        return results
    }

    /** 渲染单个 LaTeX 表达式并返回 Bitmap */
    suspend fun renderSingle(latex: String): Bitmap? {
        return withContext(Dispatchers.Main) {
            try {
                val webView = createWebView()
                var result: Bitmap? = null
                var completed = false

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.post {
                            val bitmap = captureLatexBitmap(webView, latex, displayMode = false)
                            result = bitmap
                            completed = true
                        }
                    }
                }

                webView.loadUrl("file:///android_asset/latex_render.html")

                // 等待渲染完成
                while (!completed) {
                    try {
                        kotlinx.coroutines.delay(50)
                    } catch (_: InterruptedException) {
                        break
                    }
                }

                result
            } catch (e: Exception) {
                null
            }
        }
    }

    // ---------- Private ----------

    private fun createWebView(): WebView {
        return WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                // 禁用图片加载以提升速度
                blockNetworkImage = true
                // 允许从 file:// 加载
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }

            // 确保自动尺寸
            addJavascriptInterface(JsInterface(), "AndroidBridge")
        }
    }

    /** 在已加载的 WebView 中逐个渲染所有表达式 */
    private fun renderAllSequentially(
        webView: WebView,
        expressions: List<String>,
        results: ConcurrentHashMap<String, Bitmap>
    ) {
        if (expressions.isEmpty()) return

        renderNext(webView, expressions.toList(), 0, results)
    }

    private fun renderNext(
        webView: WebView,
        expressions: List<String>,
        index: Int,
        results: ConcurrentHashMap<String, Bitmap>
    ) {
        if (index >= expressions.size) return

        val latex = expressions[index]
        val displayMode = latex.contains("\n") || latex.length > 40

        // 执行 JS 渲染
        val jsExpr = latex
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")

        webView.evaluateJavascript(
            "renderLatex('$jsExpr', $displayMode);",
        ) { _ ->
            // 等待渲染完成后获取尺寸
            webView.postDelayed({
                captureBitmap(webView)?.let { bitmap ->
                    results[latex] = bitmap
                }
                // 渲染下一个
                renderNext(webView, expressions, index + 1, results)
            }, 150)
        }
    }

    /** 捕获 WebView 当前内容的 Bitmap */
    private fun captureBitmap(webView: WebView): Bitmap? {
        return try {
            // 通过 JS 获取实际渲染尺寸
            val dimJson = webView.evaluateJavascriptSync("""
                JSON.stringify({
                    w: Math.max(document.body.scrollWidth || 100, 50),
                    h: Math.max(document.body.scrollHeight || 40, 20)
                })
            """.trimIndent())

            val dim = JSONObject(dimJson)
            val width = (dim.optInt("w", 100) * density + 0.5f).toInt() + 24
            val height = (dim.optInt("h", 40) * density + 0.5f).toInt() + 24

            if (width <= 0 || height <= 0) return null
            if (width > 2000 || height > 2000) return null

            // 测量和布局 WebView
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, width, height)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT)
            webView.draw(canvas)

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /** 渲染单个 LaTeX 并捕获 Bitmap */
    private fun captureLatexBitmap(webView: WebView, latex: String, displayMode: Boolean): Bitmap? {
        try {
            val jsExpr = latex
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")

            webView.evaluateJavascript(
                "renderLatex('$jsExpr', $displayMode);",
            ) { }

            // 等待渲染
            Thread.sleep(200)

            return captureBitmap(webView)
        } catch (e: Exception) {
            return null
        }
    }

    /** 同步执行 JS 并返回结果 */
    private fun WebView.evaluateJavascriptSync(script: String): String? {
        var result: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)

        this@LatexRenderer.runOnMainThread {
            this@LatexRenderer.runOnMainThread {
                evaluateJavascript(script) { value ->
                    result = value
                    latch.countDown()
                }
            }
        }

        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: InterruptedException) {}

        return result
    }

    private fun runOnMainThread(action: () -> Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }

    /** JavaScript 接口（预留） */
    private class JsInterface {
        @android.webkit.JavascriptInterface
        fun onRendered(width: Int, height: Int) {
            // 留给 JS 回调使用
        }
    }
}
