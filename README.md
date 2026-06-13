# 📄 Md2Docx — Markdown 转 Word

> 在 Android 手机上将 Markdown 转换为排版精美的 Word 文档，支持 LaTeX 数学公式。

[![Language](https://img.shields.io/badge/language-Kotlin-purple)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-03DAC5)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![API](https://img.shields.io/badge/minSdk-26-brightgreen)](https://developer.android.com/about/versions/8.0)

---

## ✨ 功能

| 功能 | 支持情况 |
|---|---|
| **Markdown → .docx** | ✅ 完整支持 |
| **粗体 / 斜体 / 删除线** | ✅ |
| **行内代码 / 代码块** | ✅ 等宽字体 + 浅灰底色 |
| **有序 / 无序列表** | ✅ |
| **表格** | ✅ 蓝色表头 + 边框 |
| **引用块** | ✅ 左侧竖线 + 缩进样式 |
| **超链接** | ✅ 蓝色下划线 |
| **图片** | ✅ 占位标记 |
| **LaTeX 数学公式** | ✅ 本地 KaTeX 渲染，**完全离线** |
| **深色主题** | ✅ 跟随系统设置 |
| **Material 3 动态取色** | ✅ Android 12+ |
| **SAF 文件保存** | ✅ 任意位置保存 |

---

## 📸 截图

> *（等你装上跑起来后，截个图放这里！）*

---

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17（Android Studio 内置）
- Android SDK 34
- 一台 Android 8.0+ (API 26+) 的真机或模拟器

### 安装步骤

```bash
# 1. 克隆项目
git clone https://github.com/your-username/Md2Docx.git
cd Md2Docx

# 2. 用 Android Studio 打开
# File → Open → 选择 Md2Docx 文件夹

# 3. 等待 Gradle 同步完成（首次需下载依赖，可能需要 10-20 分钟）

# 4. 连接设备或启动模拟器，点击 Run ▶️
```

或者直接下载 APK（见 Releases 页面）。

---

## 📖 使用指南

### 基本流程

```
┌─────────────────┐      ┌────────────────┐      ┌─────────────────┐
│  输入 Markdown   │ ──→  │ 自动解析 + 渲染 │ ──→  │  导出 .docx 文件 │
│  (编辑器内输入)  │      │ LaTeX → 图片   │      │  (SAF 任意位置) │
└─────────────────┘      └────────────────┘      └─────────────────┘
```

### 支持的 Markdown 语法

```markdown
# 一级标题
## 二级标题
### 三级标题

**粗体文本**    *斜体文本*    ~~删除线~~
`行内代码`    [超链接](https://example.com)

- 无序列表项
- 另一项

1. 有序列表项
2. 另一项

> 引用文本

```代码块```

| 表头 | 列2 |
|------|-----|
| 单元格 | 数据 |

---

```

### LaTeX 用法

```
行内公式：$E = mc^2$

块级公式：
$$
\int_{-\infty}^{\infty} e^{-x^2}\,dx = \sqrt{\pi}
$$
```

> 支持的 LaTeX 通过本地 KaTeX 渲染，**无需网络连接**。涵盖常见的高等数学、线性代数、微积分符号。

---

## 🏗 项目结构

```
Md2Docx/
├── app/
│   ├── src/main/
│   │   ├── java/com/md2docx/
│   │   │   ├── MainActivity.kt          # 入口 Activity
│   │   │   ├── MainApplication.kt       # Application
│   │   │   ├── converter/
│   │   │   │   ├── MarkdownConverter.kt # Markdown → 中间模型
│   │   │   │   ├── DocxGenerator.kt     # 中间模型 → .docx
│   │   │   │   └── LatexRenderer.kt     # LaTeX → Bitmap
│   │   │   ├── viewmodel/
│   │   │   │   └── MainViewModel.kt     # 业务逻辑
│   │   │   └── ui/
│   │   │       ├── screens/
│   │   │       │   └── MainScreen.kt    # 主界面
│   │   │       └── theme/
│   │   │           ├── Color.kt         # 调色板
│   │   │           ├── Theme.kt         # Material 3 主题
│   │   │           └── Type.kt          # 排版
│   │   ├── assets/
│   │   │   ├── latex_render.html        # KaTeX 渲染模板
│   │   │   └── katex/                   # 本地 KaTeX 引擎
│   │   └── res/                         # 资源文件
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

---

## 🧰 技术栈

| 组件 | 选型 | 原因 |
|---|---|---|
| UI 框架 | **Jetpack Compose + Material 3** | 现代声明式 UI，代码简洁 |
| 架构 | **MVVM (ViewModel + StateFlow)** | 官方推荐，生命周期友好 |
| Markdown 解析 | **flexmark** | Java 最完善的 MD 解析库 |
| LaTeX 渲染 | **KaTeX（本地 WebView）** | 高质量渲染，完全离线 |
| Word 生成 | **纯 Kotlin 手写 OOXML** | 无大型依赖，APK 仅 ~4MB |
| 文件保存 | **Storage Access Framework** | 免权限，任意目录保存 |

---

## ⚙️ 自定义样式

修改 `DocxGenerator.kt` 中的样式定义即可定制 Word 输出：

- **字体**：在 `word/styles.xml` 中修改 `w:ascii` 属性
- **颜色**：修改 `w:color` 或 `w:fill` 属性
- **页面大小**：修改 `w:pgSz` 的宽高（默认 A4）
- **边距**：修改 `w:pgMar`（默认 1 英寸）

---

## 🧪 构建发布

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（需配置签名）
./gradlew assembleRelease

# 生成位置
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🙏 致谢

- [flexmark](https://github.com/vsch/flexmark-java) — Java Markdown 解析器
- [KaTeX](https://katex.org/) — 最快的 Web 数学公式渲染库
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — 现代 Android UI 工具包
- [Material 3](https://m3.material.io/) — Material Design 第 3 代

---

## 📄 License

```
MIT License

Copyright (c) 2024 Md2Docx

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
