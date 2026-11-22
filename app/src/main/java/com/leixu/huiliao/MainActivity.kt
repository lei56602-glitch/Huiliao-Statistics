package com.leixu.huiliao

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 248, 252)
        window.navigationBarColor = android.graphics.Color.rgb(247, 248, 252)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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
        createDocument.launch(name)
    }

    private val createDocument by lazy {
        registerForActivityResult(ActivityResultContracts.CreateDocument(pendingMime)) { uri ->
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(pendingExport ?: byteArrayOf()) }
                    savedUri = uri
                    android.widget.Toast.makeText(this, "文件已保存", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "保存失败：${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareSavedFile() {
        val uri = savedUri ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = pendingMime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享导出文件"))
    }
}

@Composable
private fun NativeHuiliaoApp(
    records: List<ProductionRecord>,
    onSaveRecord: (ProductionRecord) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onExport: (List<List<String>>, Boolean) -> Unit,
    onShare: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_config", 0) }
    var lines by remember { mutableStateOf(prefs.getString("lines", null)?.split("|")?.filter { it.isNotBlank() } ?: DefaultLines) }
    var personsA by remember { mutableStateOf(prefs.getString("personsA", null)?.split("|")?.filter { it.isNotBlank() } ?: listOf("张伟", "李莉")) }
    var personsB by remember { mutableStateOf(prefs.getString("personsB", null)?.split("|")?.filter { it.isNotBlank() } ?: listOf("陈静", "赵雷")) }
    var page by remember { mutableIntStateOf(0) }
    var month by remember { mutableStateOf(LocalDate.now().toString().take(7)) }
    var query by remember { mutableStateOf("") }
    var manageTeam by remember { mutableStateOf("A班") }
    var showAddDialog by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf("line") }
    var addValue by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppBlue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCE4FF),
            surface = Color(0xFFF7F8FC),
            background = Color(0xFFF7F8FC),
            secondary = TeamOrange
        )
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("回料异常统计", style = MaterialTheme.typography.titleLarge, color = AppBlue, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = AppBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("统计月份", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(month, { if (it.matches(Regex("\\d{0,4}(-\\d{0,2})?"))) month = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("YYYY-MM") })
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    val icons = listOf(Icons.Default.Home, Icons.Default.Assessment, Icons.Default.BarChart, Icons.Default.CalendarMonth, Icons.Default.Search, Icons.Default.ManageAccounts)
                    PageNames.forEachIndexed { i, label ->
                        NavigationBarItem(selected = page == i, onClick = { page = i }, icon = { Icon(icons[i], null) }, label = { Text(label) })
                    }
                }
            },
            floatingActionButton = {
                if (page == 0) FloatingActionButton(onClick = { /* form submit remains explicit */ }, containerColor = AppBlue) { Icon(Icons.Default.Add, "新增") }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                when (page) {
                    0 -> EntryPage(lines, personsA, personsB, onSaveRecord)
                    1 -> DashboardPage(records.filter { it.date.startsWith(month) }, lines)
                    2 -> RankPage(records.filter { it.date.startsWith(month) })
                    3 -> WeeklyPage(records.filter { it.date.startsWith(month) })
                    4 -> RecordPage(records.filter { it.date.startsWith(month) }, query, { query = it }, onDeleteRecord, onExport, onShare)
                    else -> ManagePage(
                        lines, personsA, personsB, manageTeam,
                        { manageTeam = it },
                        { addTarget = it; addValue = ""; showAddDialog = true },
                        { value -> lines = lines - value; prefs.edit().putString("lines", lines.joinToString("|")).apply() },
                        { value -> if (manageTeam == "A班") { personsA = personsA - value; prefs.edit().putString("personsA", personsA.joinToString("|")).apply() } else { personsB = personsB - value; prefs.edit().putString("personsB", personsB.joinToString("|")).apply() } }
                    )
                }
            }
        }
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(if (addTarget == "line") "添加生产线" else "添加人员") },
                text = { OutlinedTextField(addValue, { addValue = it }, singleLine = true, label = { Text("名称") }) },
                confirmButton = {
                    TextButton(onClick = {
                        val value = addValue.trim()
                        if (value.isNotEmpty()) {
                            if (addTarget == "line" && value !in lines) { lines = lines + value; prefs.edit().putString("lines", lines.joinToString("|")).apply() }
                            if (addTarget == "person") {
                                if (manageTeam == "A班" && value !in personsA) { personsA = personsA + value; prefs.edit().putString("personsA", personsA.joinToString("|")).apply() }
                                if (manageTeam == "B班" && value !in personsB) { personsB = personsB + value; prefs.edit().putString("personsB", personsB.joinToString("|")).apply() }
                            }
                        }
                        showAddDialog = false
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
            )
        }
    }
}

@Composable
private fun EntryPage(lines: List<String>, personsA: List<String>, personsB: List<String>, onSave: (ProductionRecord) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var line by remember { mutableStateOf(lines.firstOrNull().orEmpty()) }
    var team by remember { mutableStateOf("A班") }
    var person by remember { mutableStateOf(personsA.firstOrNull().orEmpty()) }
    var model by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var deductions by remember { mutableStateOf(listOf("0", "0", "0", "0")) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val people = if (team == "A班") personsA else personsB
    val total = ((weight.toDoubleOrNull() ?: 0.0) - deductions.sumOf { it.toDoubleOrNull() ?: 0.0 }).coerceAtLeast(0.0)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("回收料异常记录") {
            OutlinedTextField(date, { date = it }, label = { Text("日期 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            ChoiceField("生产线", line, lines) { line = it }
            ChoiceField("班组", team, listOf("A班", "B班")) { team = it; person = (if (it == "A班") personsA else personsB).firstOrNull().orEmpty() }
            ChoiceField("人员", person, people) { person = it }
            OutlinedTextField(model, { model = it }, label = { Text("产品型号") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("数量 (KG)") }, modifier = Modifier.fillMaxWidth())
        }
        SectionCard("合理扣减 (KG)") {
            listOf("调机扣减", "洗机扣减", "百丝边料扣减", "自割料扣减").forEachIndexed { i, label ->
                OutlinedTextField(deductions[i], { v -> deductions = deductions.toMutableList().also { it[i] = v.filter { c -> c.isDigit() || c == '.' } } }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
            }
            Text("最终不合理数量：%.2f KG".format(total), color = AppBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        SectionCard("异常类型（多选）") {
            DefaultTypes.forEach { (category, subItems) ->
                Text(category, fontWeight = FontWeight.SemiBold)
                subItems.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { item ->
                            FilterChip(selected = "$category-$item" in selected, onClick = { selected = if ("$category-$item" in selected) selected - "$category-$item" else selected + "$category-$item" }, label = { Text(item) })
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                if (date.isNotBlank() && line.isNotBlank() && team.isNotBlank() && person.isNotBlank() && selected.isNotEmpty() && total > 0) {
                    onSave(ProductionRecord(
                        id = "${System.currentTimeMillis()}-${(0..9999).random()}",
                        date = date, productionLine = line, team = team, personnel = person,
                        productModel = model, productWeight = weight.toDoubleOrNull() ?: 0.0,
                        totalUnreasonable = total, abnormalSubItems = JSONArray(selected.toList()).toString(),
                        createTime = java.time.Instant.now().toString()
                    ))
                    model = ""; weight = ""; deductions = listOf("0", "0", "0", "0"); selected = emptySet()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("提交记录") }
    }
}

@Composable
private fun DashboardPage(records: List<ProductionRecord>, lines: List<String>) {
    val total = records.sumOf { it.totalUnreasonable }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("月度数据总览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("总记录", records.size.toString(), Modifier.weight(1f))
            MetricCard("总不合理", "%.2f KG".format(total), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("A班记录", records.count { it.team == "A班" }.toString(), Modifier.weight(1f))
            MetricCard("B班记录", records.count { it.team == "B班" }.toString(), Modifier.weight(1f))
        }
        SectionCard("生产线看板") {
            lines.forEach { name ->
                val rows = records.filter { it.productionLine == name }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, fontWeight = FontWeight.Bold)
                    Text("${rows.size} 条 · %.2f KG".format(rows.sumOf { it.totalUnreasonable }))
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RankPage(records: List<ProductionRecord>) {
    val teamTotals = listOf("A班", "B班").map { it to records.filter { r -> r.team == it }.sumOf { r -> r.totalUnreasonable } }
    val lineTotals = records.groupBy { it.productionLine }.mapValues { it.value.sumOf { r -> r.totalUnreasonable } }.toList().sortedByDescending { it.second }
    val personTotals = records.groupBy { "${it.team} · ${it.personnel}" }.mapValues { it.value.sumOf { r -> r.totalUnreasonable } }.toList().sortedByDescending { it.second }.take(8)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("班组排行") { RankedRows(teamTotals) }
        SectionCard("线别排行") { RankedRows(lineTotals) }
        SectionCard("人员排行") { RankedRows(personTotals) }
        SectionCard("异常类型分布 (KG)") {
            val stats = abnormalStats(records)
            if (stats.isEmpty()) Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else stats.forEach { (name, value) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name); Text("%.2f KG".format(value)) } }
        }
    }
}

@Composable
private fun WeeklyPage(records: List<ProductionRecord>) {
    val weeks = records.groupBy { r ->
        val d = LocalDate.parse(r.date)
        val monday = d.minusDays((d.dayOfWeek.value - 1).toLong())
        monday.toString()
    }.toSortedMap()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("每周数据统计对比", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("自然周（周一至周日）聚合", color = MaterialTheme.colorScheme.onSurfaceVariant)
        weeks.forEach { (start, rows) ->
            val a = rows.filter { it.team == "A班" }.sumOf { it.totalUnreasonable }
            val b = rows.filter { it.team == "B班" }.sumOf { it.totalUnreasonable }
            SectionCard(start + " 起") {
                Text("A班：%.2f KG".format(a), color = AppBlue)
                Text("B班：%.2f KG".format(b), color = TeamOrange)
                Text("合计：%.2f KG · %d 条".format(a + b, rows.size), fontWeight = FontWeight.Bold)
            }
        }
        Text("本月总计：%.2f KG".format(records.sumOf { it.totalUnreasonable }), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecordPage(
    records: List<ProductionRecord>, query: String, onQuery: (String) -> Unit,
    onDelete: (String) -> Unit, onExport: (List<List<String>>, Boolean) -> Unit, onShare: () -> Unit
) {
    val filtered = records.filter { listOf(it.date, it.productionLine, it.team, it.personnel, it.productModel, it.abnormalSubItems).any { s -> s.contains(query, true) } }
    Column(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
        OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索记录") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { onExport(exportRows(filtered), true) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileDownload, null); Text("导出 Excel") }
            OutlinedButton(onClick = { onExport(exportRows(filtered), false) }, modifier = Modifier.weight(1f)) { Text("导出 CSV") }
        }
        OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("分享最近导出的文件") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { r ->
                SectionCard(r.date + " · " + r.productionLine + " · " + r.team) {
                    Text("${r.personnel} · ${r.productModel}")
                    Text("%.2f KG".format(r.totalUnreasonable), color = AppBlue, fontWeight = FontWeight.Bold)
                    Text(parseSubItems(r.abnormalSubItems).joinToString("、"), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onDelete(r.id) }) { Icon(Icons.Default.Delete, null); Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun ManagePage(
    lines: List<String>, a: List<String>, b: List<String>, team: String,
    onTeam: (String) -> Unit, onAdd: (String) -> Unit,
    onDeleteLine: (String) -> Unit, onDeletePerson: (String) -> Unit
) {
    val people = if (team == "A班") a else b
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("生产线管理") {
            lines.forEach { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(it); IconButton(onClick = { onDeleteLine(it) }) { Icon(Icons.Default.Delete, null) } } }
            Button(onClick = { onAdd("line") }) { Icon(Icons.Default.Add, null); Text("添加生产线") }
        }
        SectionCard("人员管理") {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(selected = team == "A班", onClick = { onTeam("A班") }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("A班") }
                SegmentedButton(selected = team == "B班", onClick = { onTeam("B班") }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("B班") }
            }
            people.forEach { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(it); IconButton(onClick = { onDeletePerson(it) }) { Icon(Icons.Default.Delete, null) } } }
            Button(onClick = { onAdd("person") }) { Icon(Icons.Default.Add, null); Text("添加人员") }
        }
        SectionCard("异常类型配置") {
            Text("当前内置异常分类及子项已迁移到原生录入界面。分类增删和子项维护将在下一阶段接入本地配置存储。", style = MaterialTheme.typography.bodySmall)
            DefaultTypes.forEach { (name, children) -> Text(name, fontWeight = FontWeight.SemiBold); Text(children.joinToString("、"), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ChoiceField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value, {}, modifier = Modifier.fillMaxWidth().menuAnchor(), readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onSelect(it); expanded = false }) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) { Column(Modifier.padding(14.dp)) { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleLarge, color = AppBlue, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun RankedRows(rows: List<Pair<String, Double>>) {
    rows.forEachIndexed { index, item ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Text("${index + 1}", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = AppBlue, fontWeight = FontWeight.Bold) }
            Text(item.first, Modifier.weight(1f).padding(start = 10.dp))
            Text("%.2f KG".format(item.second), fontWeight = FontWeight.Bold)
        }
    }
}

private fun abnormalStats(records: List<ProductionRecord>): List<Pair<String, Double>> {
    val map = linkedMapOf<String, Double>()
    records.forEach { r ->
        val items = parseSubItems(r.abnormalSubItems)
        if (items.isNotEmpty() && r.totalUnreasonable > 0) {
            val each = r.totalUnreasonable / items.size
            items.forEach { raw -> map[raw.substringAfter("-", raw)] = (map[raw.substringAfter("-", raw)] ?: 0.0) + each }
        }
    }
    return map.toList().sortedByDescending { it.second }
}

private fun parseSubItems(raw: String): List<String> = try {
    val arr = JSONArray(raw)
    (0 until arr.length()).map { arr.getString(it) }
} catch (_: Exception) { emptyList() }

private fun exportRows(records: List<ProductionRecord>): List<List<String>> {
    val rows = mutableListOf(listOf("日期", "生产线", "班组", "人员", "产品型号", "产量(KG)", "不合理(KG)", "异常类型", "提交时间"))
    records.forEach { r ->
        rows.add(listOf(r.date, r.productionLine, r.team, r.personnel, r.productModel.ifBlank { "-" }, "%.2f".format(r.productWeight), "%.2f".format(r.totalUnreasonable), parseSubItems(r.abnormalSubItems).joinToString("、") { it.substringAfter("-", it) }, r.createTime))
    }
    return rows
}

private object NativeXlsx {
    fun csv(rows: List<List<String>>): ByteArray {
        val text = "\uFEFF" + rows.joinToString("\r\n") { row ->
            row.joinToString(",") { value -> if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value }
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    fun create(rows: List<List<String>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="回料异常" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val sheet = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                rows.forEachIndexed { rowIndex, row ->
                    append("<row r=\"${rowIndex + 1}\">")
                    row.forEachIndexed { colIndex, value ->
                        val ref = columnName(colIndex + 1) + (rowIndex + 1)
                        append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>")
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            entry("xl/worksheets/sheet1.xml", sheet)
        }
        return output.toByteArray()
    }

    private fun columnName(index: Int): String {
        var n = index; var result = ""
        while (n > 0) { val rem = (n - 1) % 26; result = ('A' + rem) + result; n = (n - 1) / 26 }
        return result
    }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
