# AI-android-app

Android 漫画阅读器，支持本地 ZIP 漫画和 WebDAV 远程漫画。

## 功能特性

- **ZIP 漫画阅读**：解析本地 ZIP 文件中的图片，支持 JPG/PNG/GIF/WebP/BMP 格式
- **双模式阅读**：水平翻页模式（HorizontalPager）和垂直长条模式（LazyColumn），自由切换
- **阅读进度记忆**：自动保存阅读进度，下次打开弹出"继续阅读"对话框
- **图片缩放**：双击缩放 2x，双指 pinch-to-zoom（1x~5x），放大后平移
- **右侧胶囊进度条**：快速拖动跳转页面，自动显示/隐藏
- **WebDAV 远程访问**：添加 WebDAV 服务器，浏览目录、下载漫画到本地
- **漫画管理**：封面显示、搜索过滤、排序（文件名/阅读时间/文件大小/页数）
- **缓存清理**：单漫画删除缓存、多选批量删除
- **OOM 防护**：图片顺序加载 + 流式写入，支持大体积 ZIP 文件
- **简繁搜索**：搜索支持简体/繁体混合匹配

## 技术栈

| 技术 | 用途 |
|------|------|
| Kotlin + Jetpack Compose | UI 框架 |
| Material3 | 设计语言 |
| MVVM (ViewModel + StateFlow) | 架构模式 |
| Navigation Compose | 页面导航 |
| Coil | 图片异步加载 |
| OkHttp 4.12 | WebDAV 网络请求 |
| Android SAF | 本地文件夹扫描 |
| SharedPreferences | 持久化存储（阅读进度/配置） |

## 项目结构

```
app/src/main/java/com/example/comicreader/
├── MainActivity.kt              # 入口 Activity
├── model/                       # 数据模型
│   ├── Comic.kt                 # 漫画模型
│   └── WebDavEntry.kt           # WebDAV 目录条目
├── repository/                  # 数据仓库
│   ├── ComicRepository.kt       # 漫画数据仓库
│   └── WebDavServerRepository.kt # WebDAV 配置持久化
├── network/
│   └── WebDavClient.kt          # WebDAV HTTP 客户端
├── util/
│   └── ZipHelper.kt             # ZIP 文件解析工具
├── viewmodel/                   # ViewModel 层
│   ├── ComicListViewModel.kt    # 漫画列表状态管理
│   ├── ComicReaderViewModel.kt  # 阅读器状态管理
│   ├── ServerSettingsViewModel.kt # WebDAV 服务器管理
│   └── WebDavBrowseViewModel.kt  # WebDAV 文件浏览
├── ui/
│   ├── navigation/
│   │   └── NavGraph.kt          # 导航图配置
│   ├── theme/
│   │   └── Theme.kt             # Material3 主题
│   └── screens/                 # 页面
│       ├── ComicListScreen.kt   # 首页漫画列表
│       ├── ChapterListScreen.kt # 章节列表
│       ├── ReaderScreen.kt      # 阅读器
│       ├── ServerSettingsScreen.kt # WebDAV 服务器管理
│       └── WebDavBrowseScreen.kt   # WebDAV 文件浏览
```

## 构建与运行

### 环境要求

- JDK 17+
- Android SDK API 34+
- Android 设备/模拟器（API 24+）

### 构建命令

```bash
export JAVA_HOME=/Users/zpl/.jdks/jdk-17.0.19+10/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$ANDROID_HOME/platform-tools:$PATH

cd AI-android-app
./gradlew assembleDebug
```

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 文档

- [功能点文档](docs/功能点文档.md) — 完整功能说明
- [跨平台实现规范](docs/跨平台实现规范.md) — 面向其他平台的实现参考
- [命令记录](commands.md) — 开发过程中使用的命令
# AI-android-app