package com.leixu.huiliao

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private var pendingBytes: ByteArray = byteArrayOf()
    private var pendingMime: String = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private lateinit var xlsxLauncher: ActivityResultLauncher<String>
    private lateinit var csvLauncher: ActivityResultLauncher<String>

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(248, 250, 252)
        window.navigationBarColor = android.graphics.Color.rgb(248, 250, 252)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        xlsxLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        ) { uri -> writeExport(uri) }
        csvLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv")
        ) { uri -> writeExport(uri) }

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.isVerticalScrollBarEnabled = true
        webView.addJavascriptInterface(ExportBridge(), "AndroidExport")
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setMessage(message.orEmpty())
                    .setPositiveButton("确定") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setMessage(message.orEmpty())
                    .setPositiveButton("确定") { _, _ -> result?.confirm() }
                    .setNegativeButton("取消") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    private inner class ExportBridge {
        @JavascriptInterface
        fun saveFile(base64: String, fileName: String, mimeType: String) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                if (bytes.isEmpty()) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "导出文件为空", Toast.LENGTH_SHORT).show() }
                    return
                }
                pendingBytes = bytes
                pendingMime = if (mimeType == "text/csv") "text/csv"
                    else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                runOnUiThread {
                    try {
                        if (pendingMime == "text/csv") csvLauncher.launch(fileName)
                        else xlsxLauncher.launch(fileName)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, "无法打开系统文件保存界面", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "准备导出失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun writeExport(uri: Uri?) {
        if (uri == null) return
        try {
            val stream = contentResolver.openOutputStream(uri) ?: error("无法打开目标文件")
            stream.use { it.write(pendingBytes) }
            Toast.makeText(this, "文件已保存到手机", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
