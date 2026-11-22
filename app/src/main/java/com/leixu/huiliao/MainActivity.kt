package com.leixu.huiliao

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    private var pendingBytes: ByteArray = byteArrayOf()
    private lateinit var xlsxLauncher: ActivityResultLauncher<String>
    private lateinit var csvLauncher: ActivityResultLauncher<String>
    private lateinit var webView: WebView

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

        webView = WebView(this)
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

    // Must be a public class for WebView's JavaScript bridge reflection.
    inner class ExportBridge {
        @JavascriptInterface
        fun saveRows(rowsJson: String, fileName: String, format: String) {
            try {
                val source = JSONArray(rowsJson)
                if (source.length() == 0) error("没有可导出的数据")
                val rows = ArrayList<List<String>>(source.length())
                for (rowIndex in 0 until source.length()) {
                    val row = source.optJSONArray(rowIndex) ?: JSONArray()
                    val values = ArrayList<String>(row.length())
                    for (columnIndex in 0 until row.length()) {
                        val value = row.opt(columnIndex)
                        values.add(if (value == null || value == org.json.JSONObject.NULL) "" else value.toString())
                    }
                    rows.add(values)
                }
                if (rows.all { it.isEmpty() }) error("导出数据为空")
                val bytes = when (format.lowercase()) {
                    "csv" -> NativeXlsx.csv(rows)
                    else -> NativeXlsx.create(rows)
                }
                if (bytes.isEmpty()) error("生成的文件为空")
                pendingBytes = bytes
                val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
                    .ifBlank { if (format.equals("csv", true)) "回料异常统计.csv" else "回料异常统计.xlsx" }
                runOnUiThread {
                    try {
                        if (format.equals("csv", true)) csvLauncher.launch(safeName)
                        else xlsxLauncher.launch(safeName)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, "无法打开系统文件保存界面", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "无法启动文件保存：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "导出准备失败：${e.message}", Toast.LENGTH_LONG).show()
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
        } finally {
            pendingBytes = byteArrayOf()
        }
    }
}
