package com.leixu.huiliao

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object NativeXlsx {
    fun csv(rows: List<List<String>>): ByteArray {
        val text = "\uFEFF" + rows.joinToString("\r\n") { row ->
            row.joinToString(",") { value ->
                if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
            }
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    fun create(rows: List<List<String>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
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
                        val cell = columnName(colIndex + 1) + (rowIndex + 1)
                        append("<c r=\"$cell\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>")
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
        var n = index
        var result = ""
        while (n > 0) {
            val rem = (n - 1) % 26
            result = ('A' + rem) + result
            n = (n - 1) / 26
        }
        return result
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
