package com.md2docx.converter

import com.vladsch.flexmark.ext.math.MathBlock
import com.vladsch.flexmark.ext.math.MathExtension
import com.vladsch.flexmark.ext.math.MathInline
import com.vladsch.flexmark.ext.tables.TableBlock
import com.vladsch.flexmark.ext.tables.TableBody
import com.vladsch.flexmark.ext.tables.TableCell
import com.vladsch.flexmark.ext.tables.TableHead
import com.vladsch.flexmark.ext.tables.TableRow
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.misc.Extension

// ============================================================
// Document Model — 中间表示层
// ============================================================

data class DocumentModel(
    val title: String = "",
    val elements: List<DocumentElement> = emptyList()
)

sealed class DocumentElement {
    data class Paragraph(val spans: List<TextSpan>) : DocumentElement()
    data class Heading(val spans: List<TextSpan>, val level: Int) : DocumentElement()
    data class CodeBlock(val code: String, val language: String? = null) : DocumentElement()
    data class BulletList(val items: List<List<DocumentElement>>) : DocumentElement()
    data class OrderedList(val items: List<List<DocumentElement>>, val startNumber: Int = 1) : DocumentElement()
    data class BlockQuote(val elements: List<DocumentElement>) : DocumentElement()
    data class MathBlock(val latex: String) : DocumentElement()
    data class Table(val rows: List<TableRowData>) : DocumentElement()
    data object HorizontalRule : DocumentElement()
    data class Image(val url: String, val alt: String? = null) : DocumentElement()
}

data class TableRowData(val cells: List<List<TextSpan>>, val isHeader: Boolean = false)

data class TextSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
    val link: String? = null,
    val math: Boolean = false
)

// ============================================================
// Markdown → DocumentModel
// ============================================================

class MarkdownConverter {

    private val parser: Parser = Parser.builder()
        .extensions(listOf(
            MathExtension.create() as Extension,
            TablesExtension.create() as Extension
        ))
        .build()

    fun parse(markdown: String): DocumentModel {
        val document = parser.parse(markdown)
        return DocumentModel(elements = collectChildren(document))
    }

    /** 提取所有 LaTeX 表达式，用于批量渲染 */
    fun extractLatex(documentModel: DocumentModel): List<String> {
        val result = mutableSetOf<String>()
        fun walk(element: DocumentElement) {
            when (element) {
                is DocumentElement.MathBlock -> result.add(element.latex)
                is DocumentElement.Paragraph -> element.spans.forEach { if (it.math) result.add(it.text) }
                is DocumentElement.Heading -> element.spans.forEach { if (it.math) result.add(it.text) }
                is DocumentElement.BulletList -> element.items.forEach { items -> items.forEach { walk(it) } }
                is DocumentElement.OrderedList -> element.items.forEach { items -> items.forEach { walk(it) } }
                is DocumentElement.BlockQuote -> element.elements.forEach { walk(it) }
                is DocumentElement.Table -> element.rows.forEach { row ->
                    row.cells.forEach { cell -> cell.forEach { if (it.math) result.add(it.text) } }
                }
                else -> {}
            }
        }
        for (elem in documentModel.elements) walk(elem)
        return result.toList()
    }

    // ================================================================
    // 手动迭代 AST（避免 NodeVisitor 递归嵌套问题）
    // ================================================================

    /** 遍历某节点的所有子节点，转换为 DocumentElement 列表 */
    private fun collectChildren(node: Node): List<DocumentElement> {
        val result = mutableListOf<DocumentElement>()
        var child = node.firstChild
        while (child != null) {
            result.addAll(nodeToElements(child))
            child = child.next
        }
        return result
    }

    /** 将单个 AST 节点转换为 DocumentElement（可能返回多个，如列表项） */
    private fun nodeToElements(node: Node): List<DocumentElement> {
        return when (node) {
            is com.vladsch.flexmark.ast.Paragraph -> {
                val spans = collectInlineSpansRecursive(node)
                if (spans.isEmpty()) emptyList()
                else listOf(DocumentElement.Paragraph(spans))
            }
            is com.vladsch.flexmark.ast.Heading -> {
                val spans = collectInlineSpansRecursive(node)
                listOf(DocumentElement.Heading(spans, node.level))
            }
            is com.vladsch.flexmark.ast.FencedCodeBlock -> {
                val code = node.contentChars.toString()
                val lang = node.info.toString().trim().ifBlank { null }
                listOf(DocumentElement.CodeBlock(code, lang))
            }
            is com.vladsch.flexmark.ast.BulletList -> {
                val items = mutableListOf<List<DocumentElement>>()
                var item = node.firstChild
                while (item != null) {
                    if (item is com.vladsch.flexmark.ast.ListItem) {
                        items.add(collectChildren(item))
                    }
                    item = item.next
                }
                listOf(DocumentElement.BulletList(items))
            }
            is com.vladsch.flexmark.ast.OrderedList -> {
                val items = mutableListOf<List<DocumentElement>>()
                var item = node.firstChild
                while (item != null) {
                    if (item is com.vladsch.flexmark.ast.ListItem) {
                        items.add(collectChildren(item))
                    }
                    item = item.next
                }
                listOf(DocumentElement.OrderedList(items, node.startNumber))
            }
            is com.vladsch.flexmark.ast.BlockQuote -> {
                listOf(DocumentElement.BlockQuote(collectChildren(node)))
            }
            is com.vladsch.flexmark.ast.ThematicBreak -> {
                listOf(DocumentElement.HorizontalRule)
            }
            is MathBlock -> {
                val latex = node.chars.toString().trim()
                listOf(DocumentElement.MathBlock(latex))
            }
            is TableBlock -> {
                val rows = mutableListOf<TableRowData>()
                var child = node.firstChild
                while (child != null) {
                    when (child) {
                        is TableHead -> collectTableRows(child, rows, isHeader = true)
                        is TableBody -> collectTableRows(child, rows, isHeader = false)
                    }
                    child = child.next
                }
                listOf(DocumentElement.Table(rows))
            }
            is com.vladsch.flexmark.ast.Image -> {
                val url = node.url.toString()
                val alt = node.text.toString().ifBlank { null }
                listOf(DocumentElement.Image(url, alt))
            }
            is com.vladsch.flexmark.ast.HtmlBlock -> {
                // 忽略原始 HTML 块
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun collectTableRows(container: Node, rows: MutableList<TableRowData>, isHeader: Boolean) {
        var child = container.firstChild
        while (child != null) {
            if (child is TableRow) {
                val cells = mutableListOf<List<TextSpan>>()
                var cell = child.firstChild
                while (cell != null) {
                    if (cell is TableCell) {
                        cells.add(collectInlineSpansRecursive(cell))
                    }
                    cell = cell.next
                }
                rows.add(TableRowData(cells, isHeader))
            }
            child = child.next
        }
    }

    /** 递归收集内联节点中的文本段，传递格式状态 */
    private fun collectInlineSpansRecursive(
        node: Node,
        bold: Boolean = false,
        italic: Boolean = false,
        strikethrough: Boolean = false,
        link: String? = null
    ): List<TextSpan> {
        val result = mutableListOf<TextSpan>()
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is com.vladsch.flexmark.ast.Text -> {
                    val text = child.chars.toString()
                    if (text.isNotBlank()) {
                        result.add(TextSpan(text, bold, italic, code = false, strikethrough, link))
                    }
                }
                is com.vladsch.flexmark.ast.SoftLineBreak,
                is com.vladsch.flexmark.ast.HardLineBreak -> {
                    result.add(TextSpan(" ", bold, italic, false, strikethrough, link))
                }
                is com.vladsch.flexmark.ast.Emphasis -> {
                    result.addAll(collectInlineSpansRecursive(child, bold, italic = true, strikethrough, link))
                }
                is com.vladsch.flexmark.ast.StrongEmphasis -> {
                    result.addAll(collectInlineSpansRecursive(child, bold = true, italic, strikethrough, link))
                }
                is com.vladsch.flexmark.ast.Strikethrough -> {
                    result.addAll(collectInlineSpansRecursive(child, bold, italic, strikethrough = true, link))
                }
                is com.vladsch.flexmark.ast.Code -> {
                    val text = child.chars.toString()
                    result.add(TextSpan(text, bold, italic, code = true, strikethrough, link))
                }
                is com.vladsch.flexmark.ast.Link -> {
                    val url = child.url.toString()
                    result.addAll(collectInlineSpansRecursive(child, bold, italic, strikethrough, url))
                }
                is MathInline -> {
                    val latex = child.chars.toString()
                    result.add(TextSpan(latex, math = true))
                }
                is com.vladsch.flexmark.ast.HtmlInline -> { /* 忽略 */ }
            }
            child = child.next
        }
        return result
    }
}
