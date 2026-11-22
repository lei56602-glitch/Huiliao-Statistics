package com.leixu.huiliao

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun RecordPage(
    records: List<ProductionRecord>,
    query: String,
    onQuery: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (List<List<String>>, Boolean) -> Unit,
    onShare: () -> Unit
) {
    val filtered = records.filter { r ->
        listOf(r.date, r.productionLine, r.team, r.personnel, r.productModel, r.abnormalSubItems).any { it.contains(query, true) }
    }.sortedByDescending { it.createTime }
    Column(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
        OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索记录") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { onExport(exportRows(filtered), true) }, modifier = Modifier.weight(1f)) { Text("导出 Excel") }
            OutlinedButton(onClick = { onExport(exportRows(filtered), false) }, modifier = Modifier.weight(1f)) { Text("导出 CSV") }
        }
        OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("分享最近导出的文件") }
        if (filtered.isEmpty()) {
            Text("当前月份暂无记录", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { r ->
                    SectionCard(r.date + " · " + r.productionLine + " · " + r.team) {
                        Text("${r.personnel} · ${r.productModel.ifBlank { "-" }}")
                        Text("%.2f KG".format(r.totalUnreasonable), color = AppBlue, fontWeight = FontWeight.Bold)
                        Text(parseSubItems(r.abnormalSubItems).joinToString("、") { it.substringAfter("-", it) }, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onDelete(r.id) }) { Icon(Icons.Default.Delete, null); Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ManagePage(
    lines: List<String>,
    personsA: List<String>,
    personsB: List<String>,
    team: String,
    onTeam: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDeleteLine: (String) -> Unit,
    onDeletePerson: (String) -> Unit
) {
    val people = if (team == "A班") personsA else personsB
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("生产线管理") {
            lines.forEach { name ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name)
                    IconButton(onClick = { onDeleteLine(name) }) { Icon(Icons.Default.Delete, null) }
                }
            }
            Button(onClick = { onAdd("line") }) { Text("添加生产线") }
        }
        SectionCard("人员管理") {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(selected = team == "A班", onClick = { onTeam("A班") }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("A班") }
                SegmentedButton(selected = team == "B班", onClick = { onTeam("B班") }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("B班") }
            }
            people.forEach { name ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name)
                    IconButton(onClick = { onDeletePerson(name) }) { Icon(Icons.Default.Delete, null) }
                }
            }
            Button(onClick = { onAdd("person") }) { Text("添加人员") }
        }
        SectionCard("异常类型配置") {
            Text("现阶段已将原有默认分类和子项迁入原生录入页。分类及子项的新增、删除、编辑仍需完成。", style = MaterialTheme.typography.bodySmall)
            DefaultTypes.forEach { (name, children) ->
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(children.joinToString("、"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun exportRows(records: List<ProductionRecord>): List<List<String>> {
    val rows = mutableListOf(listOf("日期", "生产线", "班组", "人员", "产品型号", "产量(KG)", "不合理(KG)", "异常类型", "提交时间"))
    records.forEach { r ->
        rows.add(listOf(
            r.date, r.productionLine, r.team, r.personnel,
            r.productModel.ifBlank { "-" },
            "%.2f".format(r.productWeight),
            "%.2f".format(r.totalUnreasonable),
            parseSubItems(r.abnormalSubItems).joinToString("、") { it.substringAfter("-", it) },
            r.createTime
        ))
    }
    return rows
}
