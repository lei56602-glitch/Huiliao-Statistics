package com.leixu.huiliao

import androidx.activity.ComponentActivity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-time, invisible compatibility bridge for the old WebView localStorage.
 * The new application UI remains fully native; this WebView is never displayed.
 */
internal object LegacyLocalStorageMigration {
    fun run(activity: ComponentActivity, dao: RecordDao, onFinished: () -> Unit) {
        val prefs = activity.getSharedPreferences("native_config", 0)
        if (prefs.getBoolean("legacy_migration_done", false)) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val webView = WebView(activity)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.alpha = 0f
        webView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        root.addView(webView, FrameLayout.LayoutParams(1, 1))
        val finished = AtomicBoolean(false)

        fun cleanup() {
            try { root.removeView(webView); webView.stopLoading(); webView.destroy() } catch (_: Exception) {}
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (finished.get()) return
                view.evaluateJavascript(
                    "(function(){return JSON.stringify({records:localStorage.getItem('records'),lines:localStorage.getItem('lines'),persons:localStorage.getItem('persons'),types:localStorage.getItem('ab_types')});})()"
                ) { encoded ->
                    if (!finished.compareAndSet(false, true)) return@evaluateJavascript
                    try {
                        val decoded = JSONTokener(encoded).nextValue() as? String ?: encoded
                        val payload = JSONObject(decoded)
                        val editor = prefs.edit()
                        val linesRaw = payload.optString("lines", "")
                        if (linesRaw.isNotBlank() && linesRaw != "null") {
                            val lines = JSONArray(linesRaw)
                            editor.putString("lines", (0 until lines.length()).map { lines.optString(it) }.joinToString("|"))
                        }
                        val personsRaw = payload.optString("persons", "")
                        if (personsRaw.isNotBlank() && personsRaw != "null") {
                            val persons = JSONObject(personsRaw)
                            editor.putString("personsA", jsonArrayToList(persons.optJSONArray("A班")).joinToString("|"))
                            editor.putString("personsB", jsonArrayToList(persons.optJSONArray("B班")).joinToString("|"))
                        }
                        val typesRaw = payload.optString("types", "")
                        if (typesRaw.isNotBlank() && typesRaw != "null") editor.putString("legacy_ab_types", typesRaw)
                        editor.apply()

                        val rawRecords = payload.optString("records", "")
                        val imported = parseRecords(rawRecords)
                        activity.runOnUiThread {
                            activity.lifecycleScope.launch {
                                if (imported.isNotEmpty()) dao.insertAll(imported)
                                prefs.edit().putBoolean("legacy_migration_done", true).apply()
                                cleanup()
                                onFinished()
                            }
                        }
                    } catch (_: Exception) {
                        cleanup()
                    }
                }
            }
        }
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun jsonArrayToList(array: JSONArray?): List<String> =
        if (array == null) emptyList() else (0 until array.length()).map { array.optString(it) }

    private fun parseRecords(raw: String): List<ProductionRecord> {
        if (raw.isBlank() || raw == "null") return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val date = item.optString("date")
            val line = item.optString("productionLine")
            val team = item.optString("team")
            val person = item.optString("personnel")
            if (date.isBlank() || line.isBlank() || team.isBlank() || person.isBlank()) return@mapNotNull null
            val subItems = item.optJSONArray("abnormalSubItems") ?: JSONArray()
            ProductionRecord(
                id = item.optString("id").ifBlank { "legacy-$index-$date" },
                date = date,
                productionLine = line,
                team = team,
                personnel = person,
                productModel = item.optString("productModel"),
                productWeight = item.optString("productWeight").toDoubleOrNull() ?: item.optDouble("productWeight", 0.0),
                totalUnreasonable = item.optString("totalUnreasonable").toDoubleOrNull() ?: item.optDouble("totalUnreasonable", 0.0),
                abnormalSubItems = subItems.toString(),
                createTime = item.optString("createTime").ifBlank { date + "T00:00:00" }
            )
        }
    }
}

