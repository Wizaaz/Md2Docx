package com.md2docx.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.md2docx.ui.theme.*
import com.md2docx.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 清空确认对话框状态
    var showClearDialog by remember { mutableStateOf(false) }

    // SAF 文件保存 launcher
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri ->
        if (uri != null) {
            viewModel.convertToDocx(context, uri)
        }
    }

    // 显示错误
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Md2Docx",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // 清空按钮
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = !uiState.isConverting && uiState.markdownInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "清空")
                    }
                    // 转换按钮
                    IconButton(
                        onClick = {
                            val fileName = "output.docx"
                            saveLauncher.launch(fileName)
                        },
                        enabled = !uiState.isConverting && uiState.markdownInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "转换为 Word")
                    }
                }
            )
        },
        snackbarHost = {
            if (uiState.statusMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        if (uiState.statusMessage?.startsWith("✅") == true) {
                            TextButton(onClick = { viewModel.dismissError() }) {
                                Text("关闭", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                ) {
                    Text(uiState.statusMessage ?: "")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 编辑器
                MarkdownEditor(
                    value = uiState.markdownInput,
                    onValueChange = { viewModel.updateMarkdown(it) },
                    enabled = !uiState.isConverting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // 底部状态栏
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LaTeX 提示
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "支持 $...$ 和 $$...$$ LaTeX 公式",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 字符计数
                        Text(
                            text = "${uiState.markdownInput.length} 字符",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 转换中遮罩
            AnimatedVisibility(
                visible = uiState.isConverting,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = uiState.statusMessage ?: "正在处理…",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }

    // 清空确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空内容") },
            text = { Text("确定清空编辑器中的所有内容？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearMarkdown()
                    showClearDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun MarkdownEditor(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundColor = if (isDark) md_dark_editorBackground else md_editorBackground
    val textColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF212121)
    val cursorColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .background(backgroundColor)
            .padding(12.dp)
    ) {
        val scrollState = rememberScrollState()

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = textColor
            ),
            cursorBrush = SolidColor(cursorColor),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "在此输入 Markdown…",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = textColor.copy(alpha = 0.4f)
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
