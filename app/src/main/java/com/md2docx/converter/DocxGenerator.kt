package com.md2docx.converter

import android.graphics.Bitmap
import android.util.Xml
import org.xmlpull.v1.XmlSerializer
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 从 DocumentModel 生成排版优美的 .docx 文件
 *
 * .docx 格式本质是一个 ZIP 包，包含 XML 格式的文档内容、样式、关系等。
 * 这里我们直接构建 OOXML 结构，无需 Apache POI，APK 更小。
 */
class DocxGenerator {

    private var imageCounter = 0

    // OOXML 命名空间
    companion object {
        private const val NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        private const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val NS_WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
        private const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
        private const val NS_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
        private const val NS_VML = "urn:schemas-microsoft-com:vml"

        private const val RELS_EXT = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties"
        private const val RELS_CORE = "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties"
        private const val RELS_STYLES = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
        private const val RELS_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
    }

    /**
     * 生成 DOCX 文件
     * @param model 文档模型
     * @param imageBitmaps LaTeX → Bitmap 映射
     * @param outputStream 目标输出流
     */
    fun generate(
        model: DocumentModel,
        imageBitmaps: Map<String, Bitmap>,
        outputStream: OutputStream
    ) {
        imageCounter = 0
        val imageRelations = mutableMapOf<String, String>() // latex → rId

        ZipOutputStream(outputStream).use { zip ->
            // 必须按以下顺序写 ZIP 条目（部分解析器要求）

            // 1. [Content_Types].xml
            writeContentTypes(zip)

            // 2. _rels/.rels
            writePackageRels(zip)

            // 3. word/styles.xml
            writeStyles(zip)

            // 4. word/document.xml
            writeDocument(zip, model, imageBitmaps, imageRelations)

            // 5. word/_rels/document.xml.rels
            writeDocumentRels(zip, imageRelations)

            // 6. word/media/ 中的图片文件
            writeImages(zip, imageBitmaps, imageRelations)

            // 7. docProps
            writeDocProps(zip)
        }
    }

    // ================================================================
    // [Content_Types].xml
    // ================================================================

    private fun writeContentTypes(zip: ZipOutputStream) {
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        val xml = Xml.newSerializer()
        xml.setOutput(zip, "UTF-8")
        xml.startDocument("UTF-8", null)
        xml.startTag(null, "Types")
        xml.attribute(null, "xmlns", "http://schemas.openxmlformats.org/package/2006/content-types")

        addContentType(xml, "/word/document.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml")
        addContentType(xml, "/word/styles.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml")
        addContentType(xml, "/word/settings.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml")
        addContentType(xml, "/docProps/core.xml", "application/vnd.openxmlformats-package.core-properties+xml")
        addContentType(xml, "/docProps/app.xml", "application/vnd.openxmlformats-officedocument.extended-properties+xml")
        addContentType(xml, "/_rels/.rels", "application/vnd.openxmlformats-package.relationships+xml")
        addContentType(xml, "/word/_rels/document.xml.rels", "application/vnd.openxmlformats-package.relationships+xml")
        addContentType(xml, "/word/theme/theme1.xml", "application/vnd.openxmlformats-officedocument.theme+xml")

        // 图片类型
        addContentType(xml, "/word/media/image1.png", "image/png")

        xml.endTag(null, "Types")
        xml.endDocument()
        zip.closeEntry()
    }

    private fun addContentType(xml: XmlSerializer, partName: String, contentType: String) {
        xml.startTag(null, "Override")
        xml.attribute(null, "PartName", partName)
        xml.attribute(null, "ContentType", contentType)
        xml.endTag(null, "Override")
    }

    // ================================================================
    // _rels/.rels
    // ================================================================

    private fun writePackageRels(zip: ZipOutputStream) {
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        val xml = Xml.newSerializer()
        xml.setOutput(zip, "UTF-8")
        xml.startDocument("UTF-8", null)
        xml.startTag(null, "Relationships")
        xml.attribute(null, "xmlns", NS_REL)

        addRelationship(xml, "rId1", "word/document.xml", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument")
        addRelationship(xml, "rId2", "docProps/core.xml", RELS_CORE)
        addRelationship(xml, "rId3", "docProps/app.xml", RELS_EXT)

        xml.endTag(null, "Relationships")
        xml.endDocument()
        zip.closeEntry()
    }

    private fun addRelationship(xml: XmlSerializer, id: String, target: String, type: String) {
        xml.startTag(null, "Relationship")
        xml.attribute(null, "Id", id)
        xml.attribute(null, "Type", type)
        xml.attribute(null, "Target", target)
        xml.endTag(null, "Relationship")
    }

    // ================================================================
    // word/styles.xml
    // ================================================================

    private fun writeStyles(zip: ZipOutputStream) {
        zip.putNextEntry(ZipEntry("word/styles.xml"))
        val xml = Xml.newSerializer()
        xml.setOutput(zip, "UTF-8")
        xml.startDocument("UTF-8", null)
        xml.startTag(null, "w:styles")
        xml.attribute(null, "xmlns:w", NS_W)

        // --- Normal 样式 ---
        xml.startTag(null, "w:style")
        xml.attribute(null, "w:type", "paragraph")
        xml.attribute(null, "w:styleId", "Normal")
        xml.startTag(null, "w:name")
        xml.attribute(null, "w:val", "Normal")
        xml.endTag(null, "w:name")
        xml.startTag(null, "w:rPr")
        xml.startTag(null, "w:rFonts")
        xml.attribute(null, "w:ascii", "Calibri")
        xml.attribute(null, "w:hAnsi", "Calibri")
        xml.attribute(null, "w:eastAsia", "微软雅黑")
        xml.endTag(null, "w:rFonts")
        xml.startTag(null, "w:sz")
        xml.attribute(null, "w:val", "22") // 11pt
        xml.endTag(null, "w:sz")
        xml.startTag(null, "w:szCs")
        xml.attribute(null, "w:val", "22")
        xml.endTag(null, "w:szCs")
        xml.endTag(null, "w:rPr")
        xml.startTag(null, "w:pPr")
        xml.startTag(null, "w:spacing")
        xml.attribute(null, "w:after", "160")   // 8pt after
        xml.attribute(null, "w:line", "276")    // 1.15 line spacing
        xml.attribute(null, "w:lineRule", "auto")
        xml.endTag(null, "w:spacing")
        xml.endTag(null, "w:pPr")
        xml.endTag(null, "w:style")

        // --- Heading 1 ---
        writeHeadingStyle(xml, "1", "Heading 1", "32", "Arial", true, "2D5F8A")
        // --- Heading 2 ---
        writeHeadingStyle(xml, "2", "Heading 2", "28", "Arial", true, "2D5F8A")
        // --- Heading 3 ---
        writeHeadingStyle(xml, "3", "Heading 3", "24", "Arial", true, "404040")
        // --- Heading 4 ---
        writeHeadingStyle(xml, "4", "Heading 4", "22", "Arial", true, "404040")

        // --- Code (受保护字符) ---
        xml.startTag(null, "w:style")
        xml.attribute(null, "w:type", "paragraph")
        xml.attribute(null, "w:styleId", "Code")
        xml.startTag(null, "w:name")
        xml.attribute(null, "w:val", "Code")
        xml.endTag(null, "w:name")
        xml.startTag(null, "w:basedOn")
        xml.attribute(null, "w:val", "Normal")
        xml.endTag(null, "w:basedOn")
        xml.startTag(null, "w:rPr")
        xml.startTag(null, "w:rFonts")
        xml.attribute(null, "w:ascii", "Consolas")
        xml.attribute(null, "w:hAnsi", "Consolas")
        xml.attribute(null, "w:eastAsia", "Microsoft YaHei")
        xml.endTag(null, "w:rFonts")
        xml.startTag(null, "w:sz")
        xml.attribute(null, "w:val", "20") // 10pt
        xml.endTag(null, "w:sz")
        xml.startTag(null, "w:color")
        xml.attribute(null, "w:val", "333333")
        xml.endTag(null, "w:color")
        xml.endTag(null, "w:rPr")
        xml.startTag(null, "w:pPr")
        xml.startTag(null, "w:spacing")
        xml.attribute(null, "w:before", "120")
        xml.attribute(null, "w:after", "120")
        xml.attribute(null, "w:line", "240")
        xml.endTag(null, "w:spacing")
        xml.startTag(null, "w:shd")
        xml.attribute(null, "w:val", "clear")
        xml.attribute(null, "w:fill", "F5F5F5")
        xml.attribute(null, "w:color", "auto")
        xml.endTag(null, "w:shd")
        xml.endTag(null, "w:pPr")
        xml.endTag(null, "w:style")

        // --- Block Quote ---
        xml.startTag(null, "w:style")
        xml.attribute(null, "w:type", "paragraph")
        xml.attribute(null, "w:styleId", "Quote")
        xml.startTag(null, "w:name")
        xml.attribute(null, "w:val", "Quote")
        xml.endTag(null, "w:name")
        xml.startTag(null, "w:basedOn")
        xml.attribute(null, "w:val", "Normal")
        xml.endTag(null, "w:basedOn")
        xml.startTag(null, "w:rPr")
        xml.startTag(null, "w:rFonts")
        xml.attribute(null, "w:ascii", "Georgia")
        xml.attribute(null, "w:hAnsi", "Georgia")
        xml.endTag(null, "w:rFonts")
        xml.startTag(null, "w:italic")
        xml.endTag(null, "w:italic")
        xml.startTag(null, "w:color")
        xml.attribute(null, "w:val", "555555")
        xml.endTag(null, "w:color")
        xml.endTag(null, "w:rPr")
        xml.startTag(null, "w:pPr")
        xml.startTag(null, "w:ind")
        xml.attribute(null, "w:left", "720") // 0.5in indent
        xml.attribute(null, "w:right", "720")
        xml.endTag(null, "w:ind")
        xml.startTag(null, "w:spacing")
        xml.attribute(null, "w:before", "120")
        xml.attribute(null, "w:after", "120")
        xml.endTag(null, "w:spacing")
        xml.startTag(null, "w:pBdr")
        xml.startTag(null, "w:left")
        xml.attribute(null, "w:val", "single")
        xml.attribute(null, "w:sz", "12")
        xml.attribute(null, "w:color", "CCCCCC")
        xml.attribute(null, "w:space", "8")
        xml.endTag(null, "w:left")
        xml.endTag(null, "w:pBdr")
        xml.endTag(null, "w:pPr")
        xml.endTag(null, "w:style")

        xml.endTag(null, "w:styles")
        xml.endDocument()
        zip.closeEntry()
    }

    private fun writeHeadingStyle(
        xml: XmlSerializer, id: String, name: String,
        size: String, font: String, bold: Boolean, color: String
    ) {
        xml.startTag(null, "w:style")
        xml.attribute(null, "w:type", "paragraph")
        xml.attribute(null, "w:styleId", "Heading$id")
        xml.startTag(null, "w:name")
        xml.attribute(null, "w:val", name)
        xml.endTag(null, "w:name")
        xml.startTag(null, "w:basedOn")
        xml.attribute(null, "w:val", "Normal")
        xml.endTag(null, "w:basedOn")
        xml.startTag(null, "w:next")
        xml.attribute(null, "w:val", "Normal")
        xml.endTag(null, "w:next")
        xml.startTag(null, "w:rPr")
        xml.startTag(null, "w:rFonts")
        xml.attribute(null, "w:ascii", font)
        xml.attribute(null, "w:hAnsi", font)
        xml.attribute(null, "w:eastAsia", "微软雅黑")
        xml.endTag(null, "w:rFonts")
        xml.startTag(null, "w:sz")
        xml.attribute(null, "w:val", size)
        xml.endTag(null, "w:sz")
        xml.startTag(null, "w:color")
        xml.attribute(null, "w:val", color)
        xml.endTag(null, "w:color")
        if (bold) {
            xml.startTag(null, "w:b")
            xml.endTag(null, "w:b")
        }
        xml.endTag(null, "w:rPr")
        xml.startTag(null, "w:pPr")
        xml.startTag(null, "w:spacing")
        xml.attribute(null, "w:before", "240")  // 12pt before
        xml.attribute(null, "w:after", "120")   // 6pt after
        xml.endTag(null, "w:spacing")
        xml.startTag(null, "w:keepNext")
        xml.endTag(null, "w:keepNext")
        xml.startTag(null, "w:keepLines")
        xml.endTag(null, "w:keepLines")
        xml.endTag(null, "w:pPr")
        xml.endTag(null, "w:style")
    }

    // ================================================================
    // word/document.xml — 主文档内容
    // ================================================================

    private fun writeDocument(
        zip: ZipOutputStream,
        model: DocumentModel,
        imageBitmaps: Map<String, Bitmap>,
        imageRelations: MutableMap<String, String>
    ) {
        zip.putNextEntry(ZipEntry("word/document.xml"))
        val xml = Xml.newSerializer()
        xml.setOutput(zip, "UTF-8")
        xml.startDocument("UTF-8", null)

        xml.startTag(null, "w:document")
        xml.attribute(null, "xmlns:w", NS_W)
        xml.attribute(null, "xmlns:r", NS_R)
        xml.attribute(null, "xmlns:wp", NS_WP)
        xml.attribute(null, "xmlns:a", NS_A)

        xml.startTag(null, "w:body")

        for (element in model.elements) {
            writeElement(xml, element, imageBitmaps, imageRelations)
        }

        // 末尾分节符（防止最后段落下方多余空白）
        xml.startTag(null, "w:sectPr")
        xml.startTag(null, "w:pgSz")
        xml.attribute(null, "w:w", "11906")  // A4 width
        xml.attribute(null, "w:h", "16838")  // A4 height
        xml.endTag(null, "w:pgSz")
        xml.startTag(null, "w:pgMar")
        xml.attribute(null, "w:top", "1440")    // 1 inch
        xml.attribute(null, "w:right", "1440")
        xml.attribute(null, "w:bottom", "1440")
        xml.attribute(null, "w:left", "1440")
        xml.attribute(null, "w:header", "720")
        xml.attribute(null, "w:footer", "720")
        xml.endTag(null, "w:pgMar")
        xml.endTag(null, "w:sectPr")

        xml.endTag(null, "w:body")
        xml.endTag(null, "w:document")
        xml.endDocument()
        zip.closeEntry()
    }

    private fun writeElement(
        xml: XmlSerializer,
        element: DocumentElement,
        imageBitmaps: Map<String, Bitmap>,
        imageRelations: MutableMap<String, String>
    ) {
        when (element) {
            is DocumentElement.Paragraph -> writeParagraph(xml, element.spans)
            is DocumentElement.Heading -> writeHeading(xml, element.spans, element.level)
            is DocumentElement.CodeBlock -> writeCodeBlock(xml, element.code, element.language)
            is DocumentElement.BulletList -> writeBulletList(xml, element.items, imageBitmaps, imageRelations)
            is DocumentElement.OrderedList -> writeOrderedList(xml, element.items, element.startNumber, imageBitmaps, imageRelations)
            is DocumentElement.BlockQuote -> writeBlockQuote(xml, element.elements, imageBitmaps, imageRelations)
            is DocumentElement.MathBlock -> writeMathBlock(xml, element.latex, imageBitmaps, imageRelations)
            is DocumentElement.Table -> writeTable(xml, element.rows, imageBitmaps, imageRelations)
            is DocumentElement.HorizontalRule -> writeHorizontalRule(xml)
            is DocumentElement.Image -> writeParagraphWithText(xml, "[图片: ${element.alt ?: element.url}]")
        }
    }

    // ---------- 段落 ----------

    private fun writeParagraph(xml: XmlSerializer, spans: List<TextSpan>) {
        if (spans.isEmpty()) return
        xml.startTag(null, "w:p")
        writeParagraphProperties(xml, styleId = null)
        for (span in spans) {
            if (span.math) {
                // 内联 LaTeX — 跳过，在 LaTeX 块中处理
                continue
            }
            writeRun(xml, span)
        }
        xml.endTag(null, "w:p")
    }

    private fun writeParagraphProperties(xml: XmlSerializer, styleId: String?) {
        if (styleId != null) {
            xml.startTag(null, "w:pPr")
            xml.startTag(null, "w:pStyle")
            xml.attribute(null, "w:val", styleId)
            xml.endTag(null, "w:pStyle")
            xml.endTag(null, "w:pPr")
        }
    }

    // ---------- 标题 ----------

    private fun writeHeading(xml: XmlSerializer, spans: List<TextSpan>, level: Int) {
        xml.startTag(null, "w:p")
        writeParagraphProperties(xml, "Heading$level")
        for (span in spans) {
            writeRun(xml, span)
        }
        xml.endTag(null, "w:p")
    }

    // ---------- 代码块 ----------

    private fun writeCodeBlock(xml: XmlSerializer, code: String, language: String?) {
        val lines = code.split("\n")
        for (line in lines) {
            xml.startTag(null, "w:p")
            xml.startTag(null, "w:pPr")
            xml.startTag(null, "w:pStyle")
            xml.attribute(null, "w:val", "Code")
            xml.endTag(null, "w:pStyle")
            if (line === lines.first() && language != null) {
                // 第一行显示语言提示（可选）
            }
            xml.endTag(null, "w:pPr")
            xml.startTag(null, "w:r")
            xml.startTag(null, "w:rPr")
            xml.startTag(null, "w:rFonts")
            xml.attribute(null, "w:ascii", "Consolas")
            xml.attribute(null, "w:hAnsi", "Consolas")
            xml.endTag(null, "w:rFonts")
            xml.startTag(null, "w:sz")
            xml.attribute(null, "w:val", "20")
            xml.endTag(null, "w:sz")
            xml.startTag(null, "w:color")
            xml.attribute(null, "w:val", "333333")
            xml.endTag(null, "w:color")
            xml.endTag(null, "w:rPr")
            writeTextNode(xml, if (line.isEmpty()) " " else line)
            xml.endTag(null, "w:r")
            xml.endTag(null, "w:p")
        }
    }

    // ---------- 列表 ----------

    private fun writeBulletList(
        xml: XmlSerializer,
        items: List<List<DocumentElement>>,
        imageBitmaps: Map<String, Bitmap>,
        imageRelations: MutableMap<String, String>
    ) {
        for ((index, itemElements) in items.withIndex()) {
            for (elem in itemElements) {
                xml.startTag(null, "w:p")
                xml.startTag(null, "w:pPr")
                xml.startTag(null, "w:pStyle")
                xml.attribute(null, "w:val", "ListBullet")
                xml.endTag(null, "w:pStyle")
                xml.startTag(null, "w:numPr")
                xml.startTag(null, "w:ilvl")
                xml.attribute(null, "w:val", "0")
                xml.endTag(null, "w:ilvl")
                xml.startTag(null, "w:numId")
                xml.attribute(null, "w:val", "1")
                xml.endTag(null, "w:numId")
                xml.endTag(null, "w:numPr")
                xml.endTag(null, "w:pPr")
                if (elem is DocumentElement.Paragraph) {
                    for (span in elem.spans) {
                        writeRun(xml, span)
                    }
                } else {
                    // 嵌套块直接写内容
                    when (elem) {
                        is DocumentElement.Paragraph -> {}
                        else -> writeElement(xml, elem, imageBitmaps, imageRelations)
                    }
                }
                xml.endTag(null, "w:p")
            }
        }
    }

    private fun writeOrderedList(
        xml: XmlSerializer,
        items: List<List<DocumentElement>>,
        startNumber: Int,
        imageBitmaps: Map<String, Bitmap>,
        imageRelations: MutableMap<String, String>
    ) {
        for ((index, itemElements) in items.withIndex()) {
            for (elem in itemElements) {
                xml.startTag(null, "w:p")
                xml.startTag(null, "w:pPr")
                xml.startTag(null, "w:pStyle")
                xml.attribute(null, "w:val", "ListNumber")
                xml.endTag(null, "w:pStyle")
                xml.startTag(null, "w:numPr")
                xml.startTag(null, "w:ilvl")
                xml.attribute(null, "w:val", "0")
                xml.endTag(null, "w:ilvl")
                xml.startTag(null, "w:numId")
                xml.attribute(null, "w:val", "2")
                xml.endTag(null, "w:numId")
                xml.endTag(null, "w:numPr")
                xml.endTag(null, "w:pPr")
                if (elem is DocumentElement.Paragraph) {
                    for (span in elem.spans) {
                        writeRun(xml, span)
                    }
                }
                xml.endTag(null, "w:p")
            }
        }
    }

    // ---------- 引用块 ----------

    private fun writeBlockQuote(
        xml: XmlSerializer,
        elements: List<DocumentElement>,
        imageBitmaps: Map<String, Bitmap>,
        imageRelations: MutableMap<String, String>
    ) {
        for (elem in elements) {
            xml.startTag(null, "w:p")
            xml.startTag(null, "w:pPr")
            xml.startTag(null, "w:pStyle")
            xml.attribute(null, "w:val", "Quote")
            xml.endTag(null, "w:pStyle")
            xml.endTag(null, "w:pPr")
            if (elem is DocumentElement.Paragraph) {
                for (span in elem.spans) {
                    writeRun(xml, span)
                }
            }
            xml.endTag(null, "w:p")
        }
    }

    // ---------- 数学公式（LaTeX → 图片） ----------

    private fun writeMathBlock(
        xml: XmlSerializer,
        latex: String,
        imageBitmaps: Map<String, Bitmap>,
        imageRelations: MutableMap<String, String>
    ) {
        val bitmap = imageBitmaps[latex]
        if (bitmap != null) {
            val rId = getOrCreateImageRelation(latex, imageRelations)
            writeImageParagraph(xml, bitmap, rId, latex)
        } else {
            // 回退：显示 LaTeX 代码
            xml.startTag(null, "w:p")
            xml.startTag(null, "w:pPr")
            xml.startTag(null, "w:jc")
            xml.attribute(null, "w:val", "center")
            xml.endTag(null, "w:jc")
            xml.endTag(null, "w:pPr")
            xml.startTag(null, "w:r")
            xml.startTag(null, "w:rPr")
            xml.startTag(null, "w:rFonts")
            xml.attribute(null, "w:ascii", "Consolas")
            xml.attribute(null, "w:hAnsi", "Consolas")
            xml.endTag(null, "w:rFonts")
            xml.startTag(null, "w:sz")
            xml.attribute(null, "w:val", "18")
            xml.endTag(null, "w:sz")
            xml.startTag(null, "w:color")
            xml.attribute(null, "w:val", "999999")
            xml.endTag(null, "w:color")
            xml.startTag(null, "w:i")
            xml.endTag(null, "w:i")
            xml.endTag(null, "w:rPr")
            writeTextNode(xml, "[LaTeX] $latex")
            xml.endTag(null, "w:r")
            xml.endTag(null, "w:p")
        }
    }

    // ---------- 表格 ----------

    private fun writeTable(
        xml: XmlSerializer,
        rows: List<TableRowData>,
        imageBitmaps: Map<String, Bitmap>,
        imageRelations: MutableMap<String, String>
    ) {
        xml.startTag(null, "w:tbl")

        // 表格属性
        xml.startTag(null, "w:tblPr")
        xml.startTag(null, "w:tblStyle")
        xml.attribute(null, "w:val", "TableGrid")
        xml.endTag(null, "w:tblStyle")
        xml.startTag(null, "w:tblW")
        xml.attribute(null, "w:w", "5000")
        xml.attribute(null, "w:type", "pct")
        xml.endTag(null, "w:tblW")
        xml.startTag(null, "w:tblBorders")
        for (border in listOf("top", "left", "bottom", "right", "insideH", "insideV")) {
            xml.startTag(null, "w:$border")
            xml.attribute(null, "w:val", "single")
            xml.attribute(null, "w:sz", "4")
            xml.attribute(null, "w:color", "CCCCCC")
            xml.attribute(null, "w:space", "0")
            xml.endTag(null, "w:$border")
        }
        xml.endTag(null, "w:tblBorders")
        xml.endTag(null, "w:tblPr")

        // 表格网格
        xml.startTag(null, "w:tblGrid")
        if (rows.isNotEmpty()) {
            val colCount = rows.maxOf { it.cells.size }
            for (i in 0 until colCount) {
                xml.startTag(null, "w:gridCol")
                xml.attribute(null, "w:w", "2000")
                xml.endTag(null, "w:gridCol")
            }
        }
        xml.endTag(null, "w:tblGrid")

        // 行
        for (row in rows) {
            xml.startTag(null, "w:tr")
            for (cell in row.cells) {
                xml.startTag(null, "w:tc")
                if (row.isHeader) {
                    xml.startTag(null, "w:tcPr")
                    xml.startTag(null, "w:shd")
                    xml.attribute(null, "w:val", "clear")
                    xml.attribute(null, "w:fill", "2D5F8A")
                    xml.attribute(null, "w:color", "auto")
                    xml.endTag(null, "w:shd")
                    xml.endTag(null, "w:tcPr")
                }
                for (span in cell) {
                    xml.startTag(null, "w:p")
                    if (row.isHeader) {
                        xml.startTag(null, "w:pPr")
                        xml.startTag(null, "w:jc")
                        xml.attribute(null, "w:val", "center")
                        xml.endTag(null, "w:jc")
                        xml.endTag(null, "w:pPr")
                    }
                    xml.startTag(null, "w:r")
                    if (row.isHeader) {
                        xml.startTag(null, "w:rPr")
                        xml.startTag(null, "w:b")
                        xml.endTag(null, "w:b")
                        xml.startTag(null, "w:color")
                        xml.attribute(null, "w:val", "FFFFFF")
                        xml.endTag(null, "w:color")
                        xml.endTag(null, "w:rPr")
                    }
                    writeTextNode(xml, span.text)
                    xml.endTag(null, "w:r")
                    xml.endTag(null, "w:p")
                }
                xml.endTag(null, "w:tc")
            }
            xml.endTag(null, "w:tr")
        }

        xml.endTag(null, "w:tbl")
    }

    // ---------- 水平线 ----------

    private fun writeHorizontalRule(xml: XmlSerializer) {
        xml.startTag(null, "w:p")
        xml.startTag(null, "w:pPr")
        xml.startTag(null, "w:pBdr")
        xml.startTag(null, "w:bottom")
        xml.attribute(null, "w:val", "single")
        xml.attribute(null, "w:sz", "6")
        xml.attribute(null, "w:color", "AAAAAA")
        xml.attribute(null, "w:space", "1")
        xml.endTag(null, "w:bottom")
        xml.endTag(null, "w:pBdr")
        xml.endTag(null, "w:pPr")
        xml.endTag(null, "w:p")
    }

    // ================================================================
    // Run 级别内容
    // ================================================================

    private fun writeRun(xml: XmlSerializer, span: TextSpan) {
        if (span.code) {
            // 内联代码
            xml.startTag(null, "w:r")
            xml.startTag(null, "w:rPr")
            xml.startTag(null, "w:rFonts")
            xml.attribute(null, "w:ascii", "Consolas")
            xml.attribute(null, "w:hAnsi", "Consolas")
            xml.endTag(null, "w:rFonts")
            xml.startTag(null, "w:sz")
            xml.attribute(null, "w:val", "20")
            xml.endTag(null, "w:sz")
            xml.startTag(null, "w:shd")
            xml.attribute(null, "w:val", "clear")
            xml.attribute(null, "w:fill", "F0F0F0")
            xml.attribute(null, "w:color", "auto")
            xml.endTag(null, "w:shd")
            xml.startTag(null, "w:color")
            xml.attribute(null, "w:val", "C7254E")
            xml.endTag(null, "w:color")
            if (span.bold) { xml.startTag(null, "w:b"); xml.endTag(null, "w:b") }
            if (span.italic) { xml.startTag(null, "w:i"); xml.endTag(null, "w:i") }
            xml.endTag(null, "w:rPr")
            writeTextNode(xml, span.text)
            xml.endTag(null, "w:r")
        } else {
            xml.startTag(null, "w:r")
            xml.startTag(null, "w:rPr")
            xml.startTag(null, "w:rFonts")
            xml.attribute(null, "w:ascii", "Calibri")
            xml.attribute(null, "w:hAnsi", "Calibri")
            xml.attribute(null, "w:eastAsia", "微软雅黑")
            xml.endTag(null, "w:rFonts")
            if (span.bold) { xml.startTag(null, "w:b"); xml.endTag(null, "w:b") }
            if (span.italic) { xml.startTag(null, "w:i"); xml.endTag(null, "w:i") }
            if (span.strikethrough) { xml.startTag(null, "w:strike"); xml.endTag(null, "w:strike") }
            if (span.link != null) {
                xml.startTag(null, "w:color")
                xml.attribute(null, "w:val", "1976D2")
                xml.endTag(null, "w:color")
                xml.startTag(null, "w:u")
                xml.attribute(null, "w:val", "single")
                xml.endTag(null, "w:u")
            }
            xml.endTag(null, "w:rPr")

            if (span.link != null) {
                // 超链接（仅在文档中标记颜色，简化处理）
                writeTextNode(xml, span.text)
            } else {
                writeTextNode(xml, span.text)
            }
            xml.endTag(null, "w:r")
        }
    }

    /** 带图片的段落（用于 LaTeX 公式） */
    private fun writeImageParagraph(
        xml: XmlSerializer,
        bitmap: Bitmap,
        rId: String,
        altText: String
    ) {
        xml.startTag(null, "w:p")
        // 居中
        xml.startTag(null, "w:pPr")
        xml.startTag(null, "w:jc")
        xml.attribute(null, "w:val", "center")
        xml.endTag(null, "w:jc")
        xml.endTag(null, "w:pPr")

        xml.startTag(null, "w:r")
        xml.startTag(null, "w:drawing")
        xml.startTag(null, "wp:inline")
        xml.startTag(null, "wp:extent")
        xml.attribute(null, "cx", (bitmap.width * 914400 / 96).toString())
        xml.attribute(null, "cy", (bitmap.height * 914400 / 96).toString())
        xml.endTag(null, "wp:extent")
        xml.startTag(null, "wp:effectExtent")
        xml.attribute(null, "l", "0")
        xml.attribute(null, "t", "0")
        xml.attribute(null, "r", "0")
        xml.attribute(null, "b", "0")
        xml.endTag(null, "wp:effectExtent")
        xml.startTag(null, "wp:docPr")
        xml.attribute(null, "id", rId.filter { it.isDigit() }.ifEmpty { "1" })
        xml.attribute(null, "name", "LaTeX ${rId}")
        xml.attribute(null, "descr", altText)
        xml.endTag(null, "wp:docPr")
        xml.startTag(null, "a:graphic")
        xml.attribute(null, "xmlns:a", NS_A)
        xml.startTag(null, "a:graphicData")
        xml.attribute(null, "uri", "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing")
        xml.startTag(null, "w:pic")
        xml.startTag(null, "w:nvPicPr")
        xml.startTag(null, "w:cNvPr")
        xml.attribute(null, "id", "0")
        xml.attribute(null, "name", "LaTeX Image")
        xml.endTag(null, "w:cNvPr")
        xml.endTag(null, "w:nvPicPr")
        xml.startTag(null, "w:blipFill")
        xml.startTag(null, "a:blip")
        xml.attribute(null, "r:embed", rId)
        xml.endTag(null, "a:blip")
        xml.startTag(null, "a:stretch")
        xml.startTag(null, "a:fillRect")
        xml.endTag(null, "a:fillRect")
        xml.endTag(null, "a:stretch")
        xml.endTag(null, "w:blipFill")
        xml.startTag(null, "w:spPr")
        xml.startTag(null, "a:xfrm")
        xml.startTag(null, "a:off")
        xml.attribute(null, "x", "0")
        xml.attribute(null, "y", "0")
        xml.endTag(null, "a:off")
        xml.startTag(null, "a:ext")
        xml.attribute(null, "cx", (bitmap.width * 914400 / 96).toString())
        xml.attribute(null, "cy", (bitmap.height * 914400 / 96).toString())
        xml.endTag(null, "a:ext")
        xml.endTag(null, "a:xfrm")
        xml.startTag(null, "a:prstGeom")
        xml.attribute(null, "prst", "rect")
        xml.endTag(null, "a:prstGeom")
        xml.endTag(null, "w:spPr")
        xml.endTag(null, "w:pic")
        xml.endTag(null, "a:graphicData")
        xml.endTag(null, "a:graphic")
        xml.endTag(null, "wp:inline")
        xml.endTag(null, "w:drawing")
        xml.endTag(null, "w:r")
        xml.endTag(null, "w:p")
    }

    private fun writeParagraphWithText(xml: XmlSerializer, text: String) {
        xml.startTag(null, "w:p")
        xml.startTag(null, "w:r")
        writeTextNode(xml, text)
        xml.endTag(null, "w:r")
        xml.endTag(null, "w:p")
    }

    private fun writeTextNode(xml: XmlSerializer, text: String) {
        val escaped = escapeXml(text)
        // 按空格分段以使 Word 正确处理空格
        xml.startTag(null, "w:t")
        xml.attribute(null, "xml:space", "preserve")
        xml.text(escaped)
        xml.endTag(null, "w:t")
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // ================================================================
    // 图片处理
    // ================================================================

    private fun getOrCreateImageRelation(
        latex: String,
        imageRelations: MutableMap<String, String>
    ): String {
        return imageRelations.getOrPut(latex) {
            imageCounter++
            "rIdImg$imageCounter"
        }
    }

    // ================================================================
    // word/_rels/document.xml.rels
    // ================================================================

    private fun writeDocumentRels(
        zip: ZipOutputStream,
        imageRelations: Map<String, String>
    ) {
        zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
        val xml = Xml.newSerializer()
        xml.setOutput(zip, "UTF-8")
        xml.startDocument("UTF-8", null)

        xml.startTag(null, "Relationships")
        xml.attribute(null, "xmlns", NS_REL)

        addRelationship(xml, "rIdStyle", "styles.xml", RELS_STYLES)

        var relIndex = 10
        for ((_, rId) in imageRelations) {
            val target = "media/${rId}.png"
            addRelationship(xml, rId, target, RELS_IMAGE)
        }

        xml.endTag(null, "Relationships")
        xml.endDocument()
        zip.closeEntry()
    }

    // ================================================================
    // word/media/ 图片文件
    // ================================================================

    private fun writeImages(
        zip: ZipOutputStream,
        imageBitmaps: Map<String, Bitmap>,
        imageRelations: Map<String, String>
    ) {
        // 使用 imageRelations 来确定哪些图片被实际引用了
        val relationKeys = imageRelations.keys.toSet()
        for ((latex, bitmap) in imageBitmaps) {
            if (latex in relationKeys) {
                val rId = imageRelations[latex] ?: continue
                zip.putNextEntry(ZipEntry("word/media/${rId}.png"))
                val bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                zip.write(bos.toByteArray())
                zip.closeEntry()
            }
        }
    }

    // ================================================================
    // docProps
    // ================================================================

    private fun writeDocProps(zip: ZipOutputStream) {
        // docProps/core.xml
        zip.putNextEntry(ZipEntry("docProps/core.xml"))
        val xml = Xml.newSerializer()
        xml.setOutput(zip, "UTF-8")
        xml.startDocument("UTF-8", null)
        xml.startTag(null, "cp:coreProperties")
        xml.attribute(null, "xmlns:cp", "http://schemas.openxmlformats.org/package/2006/metadata/core-properties")
        xml.attribute(null, "xmlns:dc", "http://purl.org/dc/elements/1.1/")
        xml.attribute(null, "xmlns:dcterms", "http://purl.org/dc/terms/")
        xml.attribute(null, "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
        xml.startTag(null, "dc:creator").text("Md2Docx").endTag(null, "dc:creator")
        xml.startTag(null, "cp:lastModifiedBy").text("Md2Docx").endTag(null, "cp:lastModifiedBy")
        xml.endTag(null, "cp:coreProperties")
        xml.endDocument()
        zip.closeEntry()

        // docProps/app.xml
        zip.putNextEntry(ZipEntry("docProps/app.xml"))
        val xml2 = Xml.newSerializer()
        xml2.setOutput(zip, "UTF-8")
        xml2.startDocument("UTF-8", null)
        xml2.startTag(null, "Properties")
        xml2.attribute(null, "xmlns", "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties")
        xml2.attribute(null, "xmlns:vt", "http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes")
        xml2.startTag(null, "Application").text("Md2Docx").endTag(null, "Application")
        xml2.startTag(null, "DocSecurity").text("0").endTag(null, "DocSecurity")
        xml2.startTag(null, "Lines").text("1").endTag(null, "Lines")
        xml2.startTag(null, "Paragraphs").text("1").endTag(null, "Paragraphs")
        xml2.startTag(null, "ScaleCrop").text("false").endTag(null, "ScaleCrop")
        xml2.endTag(null, "Properties")
        xml2.endDocument()
        zip.closeEntry()
    }
}
