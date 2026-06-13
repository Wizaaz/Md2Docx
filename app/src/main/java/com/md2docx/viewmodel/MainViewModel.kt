package com.md2docx.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.md2docx.converter.DocxGenerator
import com.md2docx.converter.LatexRenderer
import com.md2docx.converter.MarkdownConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val markdownInput: String = "# 欢迎使用 Md2Docx\n\n输入 Markdown 内容，然后点击右上角转换按钮导出为 Word 文档。\n\n## 支持的功能\n\n- **粗体**、*斜体*、`行内代码`\n- [超链接](https://example.com)\n- 列表（有序和无序）\n- 表格\n- 引用块\n\n## LaTeX 数学公式\n\n支持行内公式 $E = mc^2$ 和块级公式：\n\n$$\n\\int_{-\\infty}^{\\infty} e^{-x^2}\\,dx = \\sqrt{\\pi}\n$$\n\n## 代码块\n\n```kotlin\nfun hello() {\n    println(\"Hello, World!\")\n}\n```",
    val isConverting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val lastSavedPath: String? = null
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val markdownConverter = MarkdownConverter()
    private val docxGenerator = DocxGenerator()

    fun updateMarkdown(text: String) {
        _uiState.update { it.copy(markdownInput = text, statusMessage = null, errorMessage = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearMarkdown() {
        _uiState.update { it.copy(markdownInput = "", statusMessage = null, errorMessage = null) }
    }

    fun convertToDocx(context: Context, outputUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, statusMessage = "正在解析 Markdown…", errorMessage = null) }

            try {
                val markdown = _uiState.value.markdownInput
                if (markdown.isBlank()) {
                    _uiState.update { it.copy(isConverting = false, errorMessage = "请输入 Markdown 内容") }
                    return@launch
                }

                // Step 1: 解析 Markdown
                val documentModel = markdownConverter.parse(markdown)
                _uiState.update { it.copy(statusMessage = "正在渲染 LaTeX 公式…") }

                // Step 2: 提取并渲染 LaTeX
                val latexExpressions = markdownConverter.extractLatex(documentModel)
                val imageBitmaps = if (latexExpressions.isNotEmpty()) {
                    val renderer = LatexRenderer(context)
                    renderer.render(latexExpressions)
                } else {
                    emptyMap()
                }

                _uiState.update { it.copy(statusMessage = "正在生成 Word 文档…") }

                // Step 3: 生成 DOCX
                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    docxGenerator.generate(documentModel, imageBitmaps, outputStream)
                }

                _uiState.update {
                    it.copy(
                        isConverting = false,
                        statusMessage = "✅ 转换完成！",
                        lastSavedPath = outputUri.toString()
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        errorMessage = "转换失败: ${e.localizedMessage ?: "未知错误"}"
                    )
                }
            }
        }
    }
}
