package com.leixu.huiliao

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private var pendingExport: ByteArray? = null
    private var pendingMime: String = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private var savedUri: Uri? = null
    private var savedMime: String = "application/octet-stream"
    private lateinit var xlsxLauncher: ActivityResultLauncher<String>
    private lateinit var csvLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 248, 252)
        window.navigationBarColor = android.graphics.Color.rgb(247, 248, 252)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        xlsxLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        ) { uri -> writeExport(uri) }
        csvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            writeExport(uri)
        }

        val dao = HuiliaoDatabase.get(this).recordDao()
        setContent {
            val records by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
            NativeHuiliaoApp(
                records = records,
                onSaveRecord = { record -> lifecycleScope.launch { dao.insert(record) } },
                onDeleteRecord = { id -> lifecycleScope.launch { dao.delete(id) } },
                onExport = { rows, excel, month -> beginExport(rows, excel, month) },
                onShare = { shareSavedFile() }
            )
        }

        // The old WebView is used only as an invisible one-time localStorage importer.
        window.decorView.post {
            LegacyLocalStorageMigration.run(this, dao) { recreate() }
        }
    }

    private fun beginExport(rows: List<List<String>>, excel: Boolean, month: String) {
        pendingMime = if (excel) {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        } else {
            "text/csv"
        }
        pendingExport = if (excel) NativeXlsx.create(rows) else NativeXlsx.csv(rows)
        val name = "回料异常统计_${month}" + if (excel) ".xlsx" else ".csv"
        if (excel) xlsxLauncher.launch(name) else csvLauncher.launch(name)
    }

    private fun writeExport(uri: Uri?) {
        if (uri == null) return
        try {
            val stream = contentResolver.openOutputStream(uri) ?: error("无法打开目标文件")
            stream.use { it.write(pendingExport ?: byteArrayOf()) }
            savedUri = uri
            savedMime = pendingMime
            android.widget.Toast.makeText(this, "文件已保存", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "保存失败：${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun shareSavedFile() {
        val uri = savedUri ?: run {
            android.widget.Toast.makeText(this, "请先导出并保存文件", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = savedMime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享导出文件"))
    }
}
