package app.goals

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#041033")
        window.navigationBarColor = Color.parseColor("#02061c")
        val w = WebView(this)
        w.setBackgroundColor(Color.parseColor("#050d2b"))
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.addJavascriptInterface(Bridge(this), "Android")
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "http" || u.scheme == "https") {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                    return true
                }
                return false
            }
        }
        w.loadUrl("file:///android_asset/index.html")
        setContentView(w)
    }
}

class Bridge(private val c: Context) {
    @JavascriptInterface
    fun load(): String = c.getSharedPreferences("g", 0).getString("d", "") ?: ""

    @JavascriptInterface
    fun save(s: String) {
        c.getSharedPreferences("g", 0).edit().putString("d", s).apply()
        GoalsWidget.refresh(c)
    }
}
