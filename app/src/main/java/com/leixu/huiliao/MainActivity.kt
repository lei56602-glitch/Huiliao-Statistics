package com.leixu.huiliao

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val AppBlue = Color(0xFF315FCE)
private val TeamOrange = Color(0xFFC26A32)
private val DefaultLines = listOf("1号线", "2号线", "3号线", "4号线", "5号线")
private val DefaultTypes = linkedMapOf(
    "设备清洗" to listOf("停机洗机", "开机洗机", "洗机", "换单洗机", "冲螺杆"),
    "换规格/调机" to listOf("换单调机", "开机调机", "波动调机", "刮模具", "换网片", "擦辊", "卷辊"),
    "收卷/切边" to listOf("收卷废料", "收卷异常", "百丝切边", "压边异常"),
    "物料/配方" to listOf("晶点", "积料", "黑点", "配方异常", "堵网", "粘性大", "胶料", "拱胶", "不下料", "混料"),
    "设备异常" to listOf("主机调试", "冷水机", "除湿机", "温度异常", "模具温度")
)
private val PageNames = listOf("录入", "看板", "排行", "周对比", "记录", "管理")

class MainActivity : ComponentActivity() {
    private var pendingExport: ByteArray? = null
    private var pendingMime: String = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private var savedUri: Uri? = null
    private lateinit var xlsxLauncher: ActivityResultLauncher<String>
    private lateinit var csvLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 248, 252)
        window.navigationBarColor = android.graphics.Color.rgb(247, 248, 252)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        xlsxLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri -> writeExport(uri) }
        csvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> writeExport(uri) }
        val dao = HuiliaoDatabase.get(this).recordDao()
        setContent {
            val records by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
            NativeHuiliaoApp(
                records = records,
                onSaveRecord = { record -> lifecycleScope.launch { dao.insert(record) } },
                onDeleteRecord = { id -> lifecycleScope.launch { dao.delete(id) } },
                onExport = { rows, excel -> beginExport(rows, excel) },
                onShare = { shareSavedFile() }
            )
        }
    }

    private fun beginExport(rows: List<List<String>>, excel: Boolean) {
        pendingMime = if (excel) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/csv"
        pendingExport = if (excel) NativeXlsx.create(rows) else NativeXlsx.csv(rows)
        val name = "回料异常统计_${LocalDate.now()}" + if (excel) ".xlsx" else ".csv"
        if (excel) xlsxLauncher.launch(name) else csvLauncher.launch(name)
    }

    private fun writeExport(uri: Uri?) {
        if (uri == null) return
        try {
            val stream = contentResolver.openOutputStream(uri) ?: error("无法打开目标文件")
            stream.use { it.write(pendingExport ?: byteArrayOf()) }
            savedUri = uri
            android.widget.Toast.makeText(this, "文件已保存", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "保存失败：${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

}
