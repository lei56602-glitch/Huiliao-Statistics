package com.leixu.huiliao

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

internal val AppBlue = Color(0xFF315FCE)
internal val TeamOrange = Color(0xFFC26A32)
internal val DefaultLines = listOf("1号线", "2号线", "3号线", "4号线", "5号线")
internal val DefaultTypes = linkedMapOf(
    "设备清洗" to listOf("停机洗机", "开机洗机", "洗机", "换单洗机", "冲螺杆"),
    "换规格/调机" to listOf("换单调机", "开机调机", "波动调机", "刮模具", "换网片", "擦辊", "卷辊"),
    "收卷/切边" to listOf("收卷废料", "收卷异常", "百丝切边", "压边异常"),
    "物料/配方" to listOf("晶点", "积料", "黑点", "配方异常", "堵网", "粘性大", "胶料", "拱胶", "不下料", "混料"),
    "设备异常" to listOf("主机调试", "冷水机", "除湿机", "温度异常", "模具温度")
)
private val PageNames = listOf("录入", "看板", "排行", "周对比", "记录", "管理")

@Composable
internal fun NativeHuiliaoApp(
    records: List<ProductionRecord>,
    onSaveRecord: (ProductionRecord) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onExport: (List<List<String>>, Boolean, String) -> Unit,
    onShare: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_config", 0) }
    var lines by remember { mutableStateOf(prefs.getString("lines", null)?.split("|")?.filter { it.isNotBlank() } ?: DefaultLines) }
    var personsA by remember { mutableStateOf(prefs.getString("personsA", null)?.split("|")?.filter { it.isNotBlank() } ?: listOf("张伟", "李莉")) }
    var personsB by remember { mutableStateOf(prefs.getString("personsB", null)?.split("|")?.filter { it.isNotBlank() } ?: listOf("陈静", "赵雷")) }
    var types by remember { mutableStateOf(loadTypes(prefs)) }
    var page by remember { mutableIntStateOf(0) }
    var month by remember { mutableStateOf(LocalDate.now().toString().take(7)) }
    var query by remember { mutableStateOf("") }
    var manageTeam by remember { mutableStateOf("A班") }
    var showAddDialog by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf("line") }
    var addValue by remember { mutableStateOf("") }
    MaterialTheme(colorScheme = lightColorScheme(primary = AppBlue, onPrimary = Color.White, primaryContainer = Color(0xFFDCE4FF), surface = Color(0xFFF7F8FC), background = Color(0xFFF7F8FC), secondary = TeamOrange)) {
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
                    PageNames.forEachIndexed { i, label -> NavigationBarItem(selected = page == i, onClick = { page = i }, icon = { Icon(icons[i], null) }, label = { Text(label) }) }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                when (page) {
                    0 -> EntryPage(lines, personsA, personsB, types, onSaveRecord)
                    1 -> DashboardPage(records.filter { it.date.startsWith(month) }, lines)
                    2 -> RankPage(records.filter { it.date.startsWith(month) })
                    3 -> WeeklyPage(records.filter { it.date.startsWith(month) }, month)
                    4 -> RecordPage(records.filter { it.date.startsWith(month) }, query, { query = it }, onDeleteRecord, onExport, onShare, month)
                    else -> ManagePage(lines, personsA, personsB, types, manageTeam, { manageTeam = it },
                        { addTarget = it; addValue = ""; showAddDialog = true },
                        { value -> lines = lines - value; prefs.edit().putString("lines", lines.joinToString("|")).apply() },
                        { value -> if (manageTeam == "A班") { personsA = personsA - value; prefs.edit().putString("personsA", personsA.joinToString("|")).apply() } else { personsB = personsB - value; prefs.edit().putString("personsB", personsB.joinToString("|")).apply() } },
                        { category -> types = types - category; persistTypes(types, prefs) },
                        { addTarget = "type"; addValue = ""; showAddDialog = true },
                        { target, _ -> addTarget = target; addValue = ""; showAddDialog = true },
                        { category, item -> types = types.toMutableMap().also { it[category] = (it[category] ?: emptyList()) - item }; persistTypes(types, prefs) })
                }
            }
        }
        if (showAddDialog) AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(when { addTarget == "line" -> "添加生产线"; addTarget == "person" -> "添加人员"; addTarget == "type" -> "添加异常大类"; else -> "添加异常子项" }) },
            text = { OutlinedTextField(addValue, { addValue = it }, singleLine = true, label = { Text("名称") }) },
            confirmButton = { TextButton(onClick = {
                val value = addValue.trim()
                if (value.isNotEmpty()) {
                    if (addTarget == "line" && value !in lines) { lines = lines + value; prefs.edit().putString("lines", lines.joinToString("|")).apply() }
                    if (addTarget == "type" && value !in types) { types = types + (value to emptyList()); persistTypes(types, prefs) }
                    if (addTarget.startsWith("subitem:")) { val category = addTarget.substringAfter(":"); types = types.toMutableMap().also { it[category] = (it[category] ?: emptyList()) + value }; persistTypes(types, prefs) }
                    if (addTarget == "person") {
                        if (manageTeam == "A班" && value !in personsA) { personsA = personsA + value; prefs.edit().putString("personsA", personsA.joinToString("|")).apply() }
                        if (manageTeam == "B班" && value !in personsB) { personsB = personsB + value; prefs.edit().putString("personsB", personsB.joinToString("|")).apply() }
                    }
                }; showAddDialog = false
            }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun EntryPage(lines: List<String>, personsA: List<String>, personsB: List<String>, types: Map<String, List<String>>, onSave: (ProductionRecord) -> Unit) {
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
            OutlinedTextField(date, {}, enabled = false, label = { Text("日期") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
            types.forEach { (category, subItems) ->
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
        Button(onClick = {
            if (date.isNotBlank() && line.isNotBlank() && team.isNotBlank() && person.isNotBlank() && selected.isNotEmpty() && total > 0) {
                onSave(ProductionRecord("${System.currentTimeMillis()}-${(0..9999).random()}", date, line, team, person, model, weight.toDoubleOrNull() ?: 0.0, total, JSONArray(selected.toList()).toString(), java.time.Instant.now().toString()))
                model = ""; weight = ""; deductions = listOf("0", "0", "0", "0"); selected = emptySet()
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("提交记录") }
    }
}

@Composable
private fun DashboardPage(records: List<ProductionRecord>, lines: List<String>) {
    val total = records.sumOf { it.totalUnreasonable }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("月度数据总览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("总记录", records.size.toString(), Modifier.weight(1f)); MetricCard("总不合理", "%.2f KG".format(total), Modifier.weight(1f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("A班记录", records.count { it.team == "A班" }.toString(), Modifier.weight(1f)); MetricCard("B班记录", records.count { it.team == "B班" }.toString(), Modifier.weight(1f)) }
        SectionCard("生产线看板") {
            lines.forEach { name ->
                val rows = records.filter { it.productionLine == name }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, fontWeight = FontWeight.Bold); Text("${rows.size} 条 · %.2f KG".format(rows.sumOf { it.totalUnreasonable })) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RankPage(records: List<ProductionRecord>) {
    val teams = listOf("A班", "B班").map { it to records.filter { r -> r.team == it }.sumOf { r -> r.totalUnreasonable } }
    val lines = records.groupBy { it.productionLine }.mapValues { it.value.sumOf { r -> r.totalUnreasonable } }.toList().sortedByDescending { it.second }
    val people = records.groupBy { "${it.team} · ${it.personnel}" }.mapValues { it.value.sumOf { r -> r.totalUnreasonable } }.toList().sortedByDescending { it.second }.take(8)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("班组排行") { RankedRows(teams) }
        SectionCard("线别排行") { RankedRows(lines) }
        SectionCard("人员排行") { RankedRows(people) }
        listOf("总异常类型分布" to records, "A班异常分布" to records.filter { it.team == "A班" }, "B班异常分布" to records.filter { it.team == "B班" }).forEach { (title, source) ->
            SectionCard("$title (KG)") {
                val stats = abnormalStats(source)
                if (stats.isEmpty()) Text("暂无数据") else {
                    DonutChart(stats)
                    stats.forEach { (name, value) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name); Text("%.2f KG".format(value)) } }
                }
            }
        }
    }
}

@Composable
private fun WeeklyPage(records: List<ProductionRecord>, month: String) {
    val ym = runCatching { YearMonth.parse(month) }.getOrElse { YearMonth.now() }
    val first = ym.atDay(1)
    val last = ym.atEndOfMonth()
    var monday = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val weeks = mutableListOf<Triple<String, Double, Double>>()
    while (!monday.isAfter(last)) {
        val sunday = monday.plusDays(6)
        val rows = records.filter { r -> runCatching { LocalDate.parse(r.date) }.getOrNull()?.let { !it.isBefore(monday) && !it.isAfter(sunday) } == true }
        val a = rows.filter { it.team == "A班" }.sumOf { it.totalUnreasonable }
        val b = rows.filter { it.team == "B班" }.sumOf { it.totalUnreasonable }
        weeks.add(Triple("${monday.monthValue}/${monday.dayOfMonth}~${sunday.monthValue}/${sunday.dayOfMonth}", a, b))
        monday = monday.plusWeeks(1)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("每周数据统计对比", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("自然周（周一至周日）聚合", color = MaterialTheme.colorScheme.onSurfaceVariant)
        WeeklyBarChart(weeks)
        weeks.forEach { (label, a, b) ->
            SectionCard(label) { Text("A班：%.2f KG".format(a), color = AppBlue); Text("B班：%.2f KG".format(b), color = TeamOrange); Text("合计：%.2f KG".format(a + b), fontWeight = FontWeight.Bold) }
        }
        Text("本月总计：%.2f KG".format(records.sumOf { it.totalUnreasonable }), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
internal fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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

internal fun abnormalStats(records: List<ProductionRecord>): List<Pair<String, Double>> {
    val map = linkedMapOf<String, Double>()
    records.forEach { r ->
        val items = parseSubItems(r.abnormalSubItems)
        if (items.isNotEmpty() && r.totalUnreasonable > 0) {
            val each = r.totalUnreasonable / items.size
            items.forEach { raw -> val name = raw.substringAfter("-", raw); map[name] = (map[name] ?: 0.0) + each }
        }
    }
    return map.toList().sortedByDescending { it.second }
}

internal fun parseSubItems(raw: String): List<String> = try {
    val arr = JSONArray(raw); (0 until arr.length()).map { arr.getString(it) }
} catch (_: Exception) { emptyList() }

private fun loadTypes(prefs: android.content.SharedPreferences): Map<String, List<String>> {
    val raw = prefs.getString("types", null) ?: prefs.getString("legacy_ab_types", null)
    if (raw.isNullOrBlank()) return DefaultTypes
    return try {
        val array = JSONArray(raw)
        val result = linkedMapOf<String, List<String>>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val items = obj.optJSONArray("subItems") ?: JSONArray()
            result[obj.optString("name")] = (0 until items.length()).map { items.optString(it) }
        }
        if (result.isEmpty()) DefaultTypes else result
    } catch (_: Exception) { DefaultTypes }
}

private fun persistTypes(types: Map<String, List<String>>, prefs: android.content.SharedPreferences) {
    val array = JSONArray()
    types.forEach { (name, items) ->
        val obj = JSONObject()
        obj.put("id", name)
        obj.put("name", name)
        obj.put("subItems", JSONArray(items))
        array.put(obj)
    }
    prefs.edit().putString("types", array.toString()).apply()
}

@Composable
private fun DonutChart(values: List<Pair<String, Double>>) {
    val palette = listOf(AppBlue, TeamOrange, Color(0xFF0FC6C2), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF10B981))
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val total = values.sumOf { it.second }.coerceAtLeast(0.0001)
        val stroke = size.minDimension * 0.19f
        val diameter = size.minDimension * 0.78f
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        var start = -90f
        values.forEachIndexed { index, item ->
            val sweep = (item.second / total * 360.0).toFloat()
            drawArc(palette[index % palette.size], start, sweep, false, topLeft, Size(diameter, diameter), style = Stroke(width = stroke))
            start += sweep
        }
    }
}

@Composable
private fun WeeklyBarChart(weeks: List<Triple<String, Double, Double>>) {
    val maxValue = weeks.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(1.0) ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        if (weeks.isEmpty()) return@Canvas
        val base = size.height - 8.dp.toPx()
        val group = size.width / weeks.size
        val barWidth = (group * 0.22f).coerceAtLeast(4.dp.toPx())
        weeks.forEachIndexed { index, item ->
            val center = group * (index + 0.5f)
            val aHeight = ((item.second / maxValue) * (base - 8.dp.toPx())).toFloat()
            val bHeight = ((item.third / maxValue) * (base - 8.dp.toPx())).toFloat()
            drawRect(AppBlue, Offset(center - barWidth - 2.dp.toPx(), base - aHeight), Size(barWidth, aHeight))
            drawRect(TeamOrange, Offset(center + 2.dp.toPx(), base - bHeight), Size(barWidth, bHeight))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Text("A班", color = AppBlue, style = MaterialTheme.typography.labelSmall)
        Text("B班", color = TeamOrange, style = MaterialTheme.typography.labelSmall)
    }
}
