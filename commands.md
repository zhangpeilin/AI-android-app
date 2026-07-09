# Android 漫画阅读器开发 - 命令记录

本文档记录了开发 Android 漫画阅读器过程中使用的所有命令，包含详细说明。

---

## 一、项目初始化与目录创建

### 1. 创建 Android 项目目录结构
```bash
mkdir -p /Users/zpl/private/AI-project/AI-android-app/app/src/main/java/com/example/helloworld \
  /Users/zpl/private/AI-project/AI-android-app/app/src/main/res/layout \
  /Users/zpl/private/AI-project/AI-android-app/app/src/main/res/values \
  /Users/zpl/private/AI-project/AI-android-app/app/src/main/res/drawable \
  /Users/zpl/private/AI-project/AI-android-app/app/src/main/res/mipmap-hdpi \
  /Users/zpl/private/AI-project/AI-android-app/gradle/wrapper
```
**说明：**
- `mkdir -p`：递归创建目录，`-p` 参数表示如果父目录不存在则一并创建，已存在则不报错
- 一次创建了整个 Android 项目所需的所有目录结构

---

## 二、Git 版本控制

### 2. 初始化 Git 仓库并提交
```bash
cd /Users/zpl/private/AI-project/AI-android-app && git init && git add -A && git commit -m "Initial commit: Android Hello World app"
```
**说明：**
- `cd`：切换到项目目录
- `git init`：初始化新的 Git 仓库
- `git add -A`：添加所有文件到暂存区（`-A` = `--all`，包括新增、修改、删除）
- `git commit -m "..."`：提交更改，`-m` 指定提交信息

### 3. 添加远程仓库并推送
```bash
cd /Users/zpl/private/AI-project/AI-android-app && git remote add origin https://github.com/zhangpeilin/AI-android-app.git && git branch -M main && git push -u origin main
```
**说明：**
- `git remote add origin <url>`：添加名为 `origin` 的远程仓库地址
- `git branch -M main`：将当前分支重命名为 `main`（`-M` 强制重命名）
- `git push -u origin main`：推送到远程 `main` 分支，`-u` 设置上游跟踪分支

### 4. 切换为 SSH 地址推送
```bash
cd /Users/zpl/private/AI-project/AI-android-app && git remote set-url origin git@github.com:zhangpeilin/AI-android-app.git && git push -u origin main
```
**说明：**
- `git remote set-url origin <ssh-url>`：将远程仓库地址从 HTTPS 改为 SSH（GitHub 不支持 HTTPS 密码认证）

### 5. 拉取远程变更并 rebase
```bash
cd /Users/zpl/private/AI-project/AI-android-app && git pull origin main --rebase --allow-unrelated-histories
```
**说明：**
- `git pull origin main`：从远程拉取 `main` 分支
- `--rebase`：使用 rebase 而非 merge 合并，保持线性提交历史
- `--allow-unrelated-histories`：允许合并没有共同祖先的分支（本地初始化和远程仓库历史不同）

### 6. 解决冲突后继续 rebase
```bash
cd /Users/zpl/private/AI-project/AI-android-app && git add .gitignore && GIT_EDITOR="true" git rebase --continue
```
**说明：**
- `git add .gitignore`：标记冲突文件已解决
- `GIT_EDITOR="true"`：设置编辑器为 `true`（无操作），避免终端无法打开编辑器的问题
- `git rebase --continue`：继续 rebase 流程

### 7. 推送后续改动
```bash
cd /Users/zpl/private/AI-project/AI-android-app && git add -A && git commit -m "Add gradlew, gradle-wrapper.jar and fix missing launcher icon" && git push origin main
```
```bash
cd /Users/zpl/private/AI-project/AI-android-app && git add -A && git commit -m "Change hello text to Chinese: 这是我的第一个安卓程序" && git push origin main
```
**说明：** 标准的 add → commit → push 流程

---

## 三、环境检查与安装

### 8. 检查 Android SDK 环境
```bash
echo "ANDROID_HOME=$ANDROID_HOME" && echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT" && which sdkmanager 2>/dev/null || echo "sdkmanager not found" && ls ~/Library/Android/sdk 2>/dev/null || echo "No SDK found" && ls /usr/local/share/android-commandlinetools 2>/dev/null || echo "No cmdline tools found"
```
**说明：**
- `echo`：打印环境变量值
- `which sdkmanager`：查找 sdkmanager 命令路径，`2>/dev/null` 将错误输出丢弃
- `|| echo "..."`：如果前一个命令失败（返回非0），则执行 echo 提示
- `ls ... 2>/dev/null`：检查 SDK 目录是否存在

### 9. 检查 Java 版本
```bash
java -version 2>&1 || echo "Java not found"
```
**说明：**
- `java -version`：查看 Java 版本信息
- `2>&1`：将标准错误重定向到标准输出（java -version 输出到 stderr）
- `|| echo "Java not found"`：如果 java 不存在则提示

### 10. 检查 Homebrew
```bash
which brew 2>/dev/null && echo "brew found" || echo "brew not found"
```
**说明：** 检查 Homebrew 包管理器是否已安装

### 11. 安装 Android 命令行工具
```bash
brew install --cask android-commandlinetools
```
**说明：**
- `brew install`：使用 Homebrew 安装软件
- `--cask`：安装 macOS 图形界面应用或大型工具包（区别于普通 formula）
- 安装路径：`/opt/homebrew/share/android-commandlinetools`

### 12. 下载 JDK 17（通过代理）
```bash
curl -L -o /tmp/jdk17.tar.gz "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.19%2B10/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.19+10.tar.gz"
```
**说明：**
- `curl`：命令行下载工具
- `-L`：跟随重定向（GitHub 下载链接会 302 重定向）
- `-o /tmp/jdk17.tar.gz`：指定输出文件路径
- URL 中 `%2B` 是 `+` 的 URL 编码

### 13. 解压 JDK 17
```bash
mkdir -p /Users/zpl/.jdks && tar -xzf /tmp/jdk17.tar.gz -C /Users/zpl/.jdks && ls /Users/zpl/.jdks/
```
**说明：**
- `mkdir -p /Users/zpl/.jdks`：创建 JDK 存放目录
- `tar -xzf`：解压 tar.gz 文件（`-x` 解压，`-z` gzip 格式，`-f` 指定文件）
- `-C /Users/zpl/.jdks`：指定解压目标目录

### 14. 安装 Android Studio（通过代理）
```bash
export https_proxy=http://127.0.0.1:7890 && export http_proxy=http://127.0.0.1:7890 && export all_proxy=socks5://127.0.0.1:7890 && brew install --cask android-studio
```
**说明：**
- `export https_proxy/http_proxy/all_proxy`：设置代理环境变量，让 brew 和 curl 通过代理下载
- `http://127.0.0.1:7890`：本地代理地址（HTTP/HTTPS）
- `socks5://127.0.0.1:7890`：SOCKS5 代理地址
- `brew install --cask android-studio`：安装 Android Studio

### 15. 打开 Android Studio
```bash
open "/Applications/Android Studio.app"
```
**说明：**
- `open`：macOS 命令，用默认应用打开文件或启动应用
- 等同于在 Finder 中双击应用图标

---

## 四、Android SDK 配置

### 16. 接受 SDK 许可证
```bash
export JAVA_HOME=/Users/zpl/.jdks/jdk-17.0.19+10/Contents/Home && export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools && yes | sdkmanager --licenses 2>&1 | tail -5
```
**说明：**
- `export JAVA_HOME=...`：设置 JDK 17 路径（sdkmanager 需要 JDK 17+）
- `export ANDROID_HOME=...`：设置 Android SDK 根目录
- `yes |`：自动对所有许可证提示回答 "y"
- `sdkmanager --licenses`：显示并接受所有 SDK 许可证
- `2>&1 | tail -5`：合并错误输出并只显示最后 5 行

### 17. 安装 SDK 组件
```bash
export JAVA_HOME=/Users/zpl/.jdks/jdk-17.0.19+10/Contents/Home && export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools && sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools" 2>&1
```
**说明：**
- `sdkmanager`：Android SDK 包管理工具
- `"platforms;android-34"`：安装 Android 14 (API 34) 平台
- `"build-tools;34.0.0"`：安装 34.0.0 版本的构建工具（aapt、dx 等）
- `"platform-tools"`：安装平台工具（adb、fastboot 等）

---

## 五、Gradle 构建

### 18. 创建 gradlew 并构建 APK
```bash
cd /Users/zpl/private/AI-project/AI-android-app && chmod +x gradlew && JAVA_HOME=/Users/zpl/.jdks/jdk-17.0.19+10/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew assembleDebug 2>&1
```
**说明：**
- `chmod +x gradlew`：给 gradlew 脚本添加执行权限
- `JAVA_HOME=...`：临时设置 JDK 17 环境变量（仅对当前命令生效）
- `ANDROID_HOME=...`：临时设置 Android SDK 路径
- `./gradlew assembleDebug`：执行 Gradle 构建调试版 APK
  - `./gradlew`：Gradle Wrapper 脚本，自动下载指定版本的 Gradle
  - `assembleDebug`：构建 debug 变体的 APK
- `2>&1`：将标准错误合并到标准输出，便于查看完整日志

### 19. 查看生成的 APK
```bash
ls -lh /Users/zpl/private/AI-project/AI-android-app/app/build/outputs/apk/debug/app-debug.apk
```
**说明：**
- `ls -lh`：列出文件详情（`-l` 详细格式，`-h` 人类可读文件大小）

---

## 六、ADB 设备操作

### 20. 查看连接的设备
```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools && export PATH=$ANDROID_HOME/platform-tools:$PATH && adb devices
```
**说明：**
- `export PATH=$ANDROID_HOME/platform-tools:$PATH`：将 platform-tools 加入 PATH，使 `adb` 命令可用
- `adb devices`：列出所有已连接的 Android 设备
  - 输出格式：`设备序列号    状态`（device = 已连接，offline = 离线）

### 21. 安装 APK 到手机
```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools && export PATH=$ANDROID_HOME/platform-tools:$PATH && adb install -r /Users/zpl/private/AI-project/AI-android-app/app/build/outputs/apk/debug/app-debug.apk 2>&1
```
**说明：**
- `adb install`：安装 APK 到设备
- `-r`：覆盖安装（replace），如果应用已存在则替换
- 后面跟 APK 文件的绝对路径

### 22. 启动 App
```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools && export PATH=$ANDROID_HOME/platform-tools:$PATH && adb shell am start -n com.example.comicreader/.MainActivity 2>&1
```
**说明：**
- `adb shell`：在设备上执行 shell 命令
- `am start`：Activity Manager 启动 Activity
- `-n com.example.comicreader/.MainActivity`：指定组件名（包名/Activity类名）

### 23. 查看崩溃日志
```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools && export PATH=$ANDROID_HOME/platform-tools:$PATH && adb logcat -d -s AndroidRuntime:E | tail -60
```
**说明：**
- `adb logcat`：查看设备日志
- `-d`：dump 模式，输出当前日志后退出（不持续监听）
- `-s AndroidRuntime:E`：过滤只显示 `AndroidRuntime` 标签的 Error 级别日志
- `| tail -60`：只显示最后 60 行

### 24. 清空日志缓存并重启 App
```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools && export PATH=$ANDROID_HOME/platform-tools:$PATH && adb logcat -c && adb shell am start -n com.example.comicreader/.MainActivity 2>&1
```
**说明：**
- `adb logcat -c`：清空（clear）设备上的日志缓冲区，确保后续只看到新的日志
- `&&`：前一个命令成功后执行下一个命令
- 用于调试前清理环境，避免旧日志干扰

### 25. 按关键词过滤 App 日志
```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools && export PATH=$ANDROID_HOME/platform-tools:$PATH && adb logcat -d | grep -iE "ZipHelper|ComicRepo|chapter|getChapters" | tail -40
```
**说明：**
- `adb logcat -d`：dump 当前所有日志
- `| grep -iE "..."`：通过正则表达式过滤（`-i` 忽略大小写，`-E` 扩展正则）
- `ZipHelper|ComicRepo|chapter`：匹配多个关键词，`|` 表示"或"
- `| tail -40`：只显示最后 40 行
- 用于定位特定模块的日志输出

### 26. 按进程 ID 过滤日志
```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools && export PATH=$ANDROID_HOME/platform-tools:$PATH && adb logcat -d | grep "16926" | grep -v "NetworkQoe\|AGPService" | tail -30
```
**说明：**
- `grep "16926"`：按 App 进程 PID 过滤（PID 可通过 `adb shell pidof com.example.comicreader` 获取）
- `grep -v "NetworkQoe\|AGPService"`：排除系统噪音标签（`-v` 反向匹配）
- 当 `-s` 标签过滤无法匹配自定义 Log 标签时，用 PID 过滤更可靠

---

## 七、进程管理

### 27. 查找并终止残留的 brew 进程
```bash
ps aux | grep "brew" | grep -v grep | grep -v "grafana\|erlang" | awk '{print $2}' | xargs kill -9 2>/dev/null
```
**说明：**
- `ps aux`：列出所有进程（`a` 所有用户，`u` 用户格式，`x` 包含无终端的进程）
- `grep "brew"`：筛选包含 "brew" 的进程
- `grep -v grep`：排除 grep 自身进程（`-v` 反向匹配）
- `grep -v "grafana\|erlang"`：排除 grafana 和 erlang 相关进程（名字中包含 brew 路径但不是 brew）
- `awk '{print $2}'`：提取第 2 列（PID 进程号）
- `xargs kill -9`：将 PID 传给 kill 命令强制终止（`-9` = SIGKILL）
- `2>/dev/null`：丢弃错误输出

### 28. 清理 brew 下载锁文件
```bash
find /Users/zpl/Library/Caches/Homebrew/downloads/ -name "*.incomplete" -delete 2>/dev/null
```
**说明：**
- `find`：文件查找命令
- `-name "*.incomplete"`：匹配文件名以 `.incomplete` 结尾的文件（brew 下载中的临时文件）
- `-delete`：直接删除找到的文件
- 这些文件是 brew 下载中断后残留的锁文件，会导致后续安装失败

---

## 八、文件操作

### 29. 删除旧文件
```bash
# 通过工具删除以下文件：
# - app/src/main/java/com/example/helloworld/MainActivity.kt
# - app/src/main/res/layout/activity_main.xml
# - app/src/main/res/values/colors.xml
```
**说明：** 迁移到 Compose 后，旧的 XML 布局和 HelloWorld 代码不再需要

---

## 九、常用环境变量速查

| 变量 | 值 | 用途 |
|------|-----|------|
| `JAVA_HOME` | `/Users/zpl/.jdks/jdk-17.0.19+10/Contents/Home` | JDK 17 路径，Android 构建必需 |
| `ANDROID_HOME` | `/opt/homebrew/share/android-commandlinetools` | Android SDK 根目录 |
| `PATH` | 追加 `$ANDROID_HOME/platform-tools` | 使 adb、sdkmanager 等命令可用 |
| `https_proxy` | `http://127.0.0.1:7890` | HTTPS 代理（brew/curl 下载用） |
| `http_proxy` | `http://127.0.0.1:7890` | HTTP 代理 |
| `all_proxy` | `socks5://127.0.0.1:7890` | SOCKS5 代理 |

---

## 十、Git 提交与推送（漫画阅读器迁移）

### 30. 提交漫画阅读器迁移代码
```bash
cd /Users/zpl/private/AI-project/AI-android-app && git add -A && git commit -m "feat: 迁移Spring Boot漫画阅读器到Android" && git push origin main
```
**说明：**
- `git add -A`：暂存所有改动（新增、修改、删除）
- `git commit -m "feat: ..."`：提交并附带详细的改动说明，包括：
  - 架构选择（Jetpack Compose + MVVM）
  - 每个新增文件的职责说明
  - 每个修改文件的改动内容
  - 所有 Bug 修复的说明
- `git push origin main`：推送到远程仓库

---

## 十一、完整构建流程（一键命令）

```bash
# 设置环境变量
export JAVA_HOME=/Users/zpl/.jdks/jdk-17.0.19+10/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$ANDROID_HOME/platform-tools:$PATH

# 构建
cd /Users/zpl/private/AI-project/AI-android-app
./gradlew assembleDebug

# 安装到手机
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 启动 App
adb shell am start -n com.example.comicreader/.MainActivity
```

## 十二、调试技巧总结

```bash
# 1. 查看崩溃堆栈（App闪退时）
adb logcat -d -s AndroidRuntime:E | tail -60

# 2. 清空日志后重新操作（精确定位问题）
adb logcat -c
adb shell am start -n com.example.comicreader/.MainActivity
# ... 在手机上操作 ...
adb logcat -d | grep -iE "关键词1|关键词2" | tail -40

# 3. 按 PID 过滤（自定义 Log 标签不生效时）
adb shell pidof com.example.comicreader  # 先获取 PID
adb logcat -d | grep "<PID>" | grep -v "系统噪音" | tail -30

# 4. 构建 + 安装 + 启动 一条龙
cd /Users/zpl/private/AI-project/AI-android-app && \
  JAVA_HOME=/Users/zpl/.jdks/jdk-17.0.19+10/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
  ./gradlew assembleDebug && \
  adb install -r app/build/outputs/apk/debug/app-debug.apk && \
  adb shell am start -n com.example.comicreader/.MainActivity
```

