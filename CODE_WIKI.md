# MCP_Run - Code Wiki 文档

> **项目名称**: MCP_Run  
> **版本**: 1.1  
> **包名**: `com.mcp_run`  
> **语言**: Java (Android)  
> **构建工具**: Gradle  
> **最低 SDK**: 21 (Android 5.0)  
> **目标 SDK**: 34 (Android 14)  
> **编译 SDK**: 36  

---

## 目录

1. [项目概述](#1-项目概述)
2. [安全审计报告](#2-安全审计报告)
3. [整体架构](#3-整体架构)
4. [模块职责](#4-模块职责)
5. [关键类与函数说明](#5-关键类与函数说明)
6. [依赖关系](#6-依赖关系)
7. [项目运行方式](#7-项目运行方式)
8. [权限清单](#8-权限清单)
9. [MCP 协议工具列表](#9-mcp-协议工具列表)

---

## 1. 项目概述

MCP_Run 是一个 **Android 应用**，实现了 **Model Context Protocol (MCP)** 协议的 **Streamable HTTP 传输标准**。它在 Android 设备上启动一个本地 HTTP 服务器（绑定 `127.0.0.1`），允许 AI 客户端通过标准化的 JSON-RPC 2.0 协议远程调用设备上的各种能力，包括：

- 文件系统操作（读写、搜索、目录管理）
- Shell 命令执行
- 设备信息获取
- 应用管理（安装、卸载、启动）
- Python/JavaScript 脚本执行
- 网络请求代理
- 剪贴板读写、通知发送
- 截屏、图片处理
- JSON/文本格式化工具

### 核心价值

将 Android 设备变成一个可编程的 MCP 工具服务器，使 AI 模型可以直接与设备交互。

---

## 2. 安全审计报告

### 2.1 审计结论

| 类别 | 结果 |
|------|------|
| 后门/病毒 | **未发现** |
| 恶意代码 | **未发现** |
| 数据外泄 | **未发现** |
| 隐藏网络通信 | **未发现** |
| 代码混淆隐藏 | **未发现** |
| 可疑外部依赖 | **未发现** |

### 2.2 详细分析

**代码透明度**: 全部代码为纯 Java 明文，无可疑的混淆、加密或反射调用。所有网络操作均通过显式的 HTTP 工具暴露，由用户主动控制。

**网络绑定**: 服务器严格绑定 `127.0.0.1`（[MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L80-L81)），仅本机可访问，外部无法直接连接。

**未发现风险点**:
- 无 C2（Command & Control）通信
- 无隐蔽的数据上传
- 无键盘记录、无屏幕监控
- 无短信/通话拦截
- 无隐藏的后台进程（除声明的前台服务外）

### 2.3 需要关注的安全设计点

| 风险点 | 说明 | 风险等级 |
|--------|------|---------|
| Shell 执行 | `shell`、`shizuku_shell` 工具可执行任意命令 | 中（但这是工具的设计目的，不是后门） |
| 明文 HTTP | `usesCleartextTraffic="true"` | 低（仅 localhost，无外部暴露） |
| 开机自启 | `RECEIVE_BOOT_COMPLETED` 权限 | 低（仅声明，代码中未实现自启逻辑） |
| Telegram 联系 | 硬编码 `@PAOLU_GGG` | 极低（仅用于用户支持） |
| 文件系统访问 | 可读写设备任意文件 | 中（工具设计目的，由用户通过 MCP 协议控制） |

> **结论**: 该项目是**干净**的。没有后门、病毒或恶意代码。所有功能都是 MCP 协议服务器的正常功能实现。

---

## 3. 整体架构

```
┌─────────────────────────────────────────────────┐
│                   Android 应用层                  │
│                                                  │
│  ┌──────────────┐  ┌──────────────────────────┐ │
│  │ MainActivity  │  │   ToolListActivity       │ │
│  │ (主控制界面)   │  │   (工具列表展示)          │ │
│  └──────┬───────┘  └──────────────────────────┘ │
│         │ 启动/停止                               │
│  ┌──────▼───────┐                                │
│  │  MCPService   │  ← Android 前台服务           │
│  │  (服务容器)    │    保持服务器存活              │
│  └──────┬───────┘                                │
│         │ 持有                                    │
│  ┌──────▼──────────┐                             │
│  │  MCPHttpServer   │  ← MCP 协议核心             │
│  │  (HTTP 服务器)    │    Streamable HTTP         │
│  │  bind: 127.0.0.1 │    实现 JSON-RPC 2.0       │
│  └──────┬──────────┘                             │
│         │ 使用                                     │
│  ┌──────▼──────────┐                             │
│  │  ToolRegistry    │  ← 工具注册与调度            │
│  │  (工具注册中心)   │    管理 60+ 个工具           │
│  └──────┬──────────┘                             │
│         │ 持有                                     │
│  ┌──────▼──────────┐                             │
│  │   FileSession    │  ← 文件会话管理              │
│  │   (工作目录状态)  │    pwd/cd/root 状态          │
│  └─────────────────┘                             │
│                                                  │
│  ┌──────────────────────────────────────────────┐│
│  │              MCPTool 接口实现                  ││
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐     ││
│  │  │文件系统工具│ │系统管理工具│ │设备信息工具│     ││
│  │  ├──────────┤ ├──────────┤ ├──────────┤     ││
│  │  │应用管理工具│ │脚本执行工具│ │通讯交互工具│     ││
│  │  ├──────────┤ ├──────────┤ ├──────────┤     ││
│  │  │网络请求工具│ │ 实用工具  │ │图片处理工具│     ││
│  │  └──────────┘ └──────────┘ └──────────┘     ││
│  └──────────────────────────────────────────────┘│
└─────────────────────────────────────────────────┘
```

### 请求流程

```
AI 客户端 ──HTTP POST──▶ 127.0.0.1:1145/mcp
                              │
                    ┌─────────▼─────────┐
                    │  MCPHttpServer     │
                    │  handleStreamableHttp()
                    │  解析 JSON-RPC 2.0  │
                    └─────────┬─────────┘
                              │
                    ┌─────────▼─────────┐
                    │  ToolRegistry      │
                    │  查找对应工具       │
                    └─────────┬─────────┘
                              │
                    ┌─────────▼─────────┐
                    │  MCPTool.execute() │
                    │  执行具体工具逻辑   │
                    └─────────┬─────────┘
                              │
                    ┌─────────▼─────────┐
                    │  JSON-RPC Response │
                    │  返回给客户端       │
                    └───────────────────┘
```

---

## 4. 模块职责

### 4.1 核心模块

| 模块 | 文件 | 职责 |
|------|------|------|
| **协议服务器** | [MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java) | 实现 MCP Streamable HTTP 协议，JSON-RPC 2.0 路由，处理 initialize/tools/list/tools/call 等请求 |
| **前台服务** | [MCPService.java](file:///workspace/app/src/main/java/com/mcp_run/MCPService.java) | Android 前台服务，保持 HTTP 服务器在后台运行，显示通知 |
| **工具注册** | [ToolRegistry.java](file:///workspace/app/src/main/java/com/mcp_run/tools/ToolRegistry.java) | 注册所有 60+ 个 MCP 工具，管理分类和查找 |
| **文件会话** | [FileSession.java](file:///workspace/app/src/main/java/com/mcp_run/tools/FileSession.java) | 管理当前工作目录和根目录，支持路径解析 |
| **工具接口** | [MCPTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/MCPTool.java) | 所有 MCP 工具的抽象接口 |

### 4.2 UI 模块

| 模块 | 文件 | 职责 |
|------|------|------|
| **主界面** | [MainActivity.java](file:///workspace/app/src/main/java/com/mcp_run/MainActivity.java) | 服务器启停控制、端口配置、工作区设置、日志查看、状态显示 |
| **工具列表** | [ToolListActivity.java](file:///workspace/app/src/main/java/com/mcp_run/ToolListActivity.java) | 展示所有 MCP 工具及其参数说明 |

### 4.3 工具模块（8 大分类）

| 分类 | 负责的工具类 | 工具数量 |
|------|-------------|---------|
| 文件操作 | FileSystemTool, FileContentTool, FileReadTool, FileWriteTool, FileDeleteTool, FileMoveTool, FileListTool | ~27 |
| 系统管理 | ShellTool, SystemControlTool, SysTool | 6 |
| 设备信息 | DeviceInfoTool, ScreenshotTool, ImageInfoTool, ImageTool | 9 |
| 应用管理 | AppManagerTool, ApkInstallTool | 2 |
| 脚本执行 | ScriptTool, PythonExecuteTool | 3 |
| 通讯交互 | ClipboardTool, NotificationTool | 2 |
| 网络请求 | HttpTool | 7 |
| 实用工具 | JsonFormatTool, TextConvertTool, InfoTool, BatchOpsTool | 12 |

---

## 5. 关键类与函数说明

### 5.1 MCPHttpServer

**核心类**，实现完整的 MCP Streamable HTTP 协议。

[文件路径](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java)

| 方法 | 说明 |
|------|------|
| `start()` | 启动服务器，绑定 `127.0.0.1:port`，创建线程池 |
| `stop()` | 停止服务器，关闭 Socket 和线程池 |
| `handleClient(Socket)` | 处理客户端连接，解析 HTTP 请求 |
| `handleStreamableHttp(OutputStream, String, Map)` | **核心方法**：处理 MCP JSON-RPC 请求 |
| `handleInitialize(JSONObject, Object)` | 处理 `initialize` 请求，返回服务器能力 |
| `handleToolsList(Object)` | 处理 `tools/list` 请求，返回所有工具 Schema |
| `handleToolsCall(JSONObject, Object)` | 处理 `tools/call` 请求，执行具体工具 |
| `handleResourcesList(Object)` | 处理 `resources/list` 请求 |
| `handleResourcesRead(JSONObject, Object)` | 处理 `resources/read` 请求 |

**关键常量**:
- `LOCALHOST = "127.0.0.1"` — 仅本地访问
- `MCP_VERSION = "2025-03-26"` — MCP 协议版本
- `DEFAULT_PORT = 1145` — 默认端口

### 5.2 MCPService

**Android 前台服务**，作为 MCPHttpServer 的宿主容器。

[文件路径](file:///workspace/app/src/main/java/com/mcp_run/MCPService.java)

| 方法 | 说明 |
|------|------|
| `onStartCommand(Intent, int, int)` | 启动服务，接收端口参数，创建前台通知，启动 HTTP 服务器 |
| `onDestroy()` | 停止服务器，清理资源 |
| `isRunning()` | 静态方法，检查服务是否运行中 |
| `createNotification(String, boolean)` | 创建前台服务通知 |

### 5.3 MainActivity

**主界面**，提供服务器控制面板。

[文件路径](file:///workspace/app/src/main/java/com/mcp_run/MainActivity.java)

| 方法 | 说明 |
|------|------|
| `startServer()` | 验证端口，启动 MCPService 前台服务 |
| `stopServer()` | 发送停止信号给 MCPService |
| `updateServerStatus(boolean)` | 更新 UI 状态指示器 |
| `addLog(String)` | 添加带时间戳的日志 |
| `requestPermissionsOnFirstLaunch()` | 首次启动时请求必要权限 |

### 5.4 ToolRegistry

**工具注册中心**，管理 60+ 个 MCP 工具。

[文件路径](file:///workspace/app/src/main/java/com/mcp_run/tools/ToolRegistry.java)

| 方法 | 说明 |
|------|------|
| `registerAll(Context)` | 注册所有工具到 8 个分类 |
| `getTool(String)` | 按名称查找工具 |
| `getAllTools()` | 获取所有工具列表 |
| `getToolsByCategory(String)` | 按分类获取工具 |
| `getToolCount()` | 获取工具总数 |

### 5.5 FileSession

**文件会话**，维护工作目录状态。

[文件路径](file:///workspace/app/src/main/java/com/mcp_run/tools/FileSession.java)

| 方法 | 说明 |
|------|------|
| `resolve(String)` | 将相对路径解析为绝对路径 |
| `setRootDir(String)` | 设置根目录 |
| `setCurrentDir(String)` | 切换当前目录 |
| `getCurrentDir()` / `getRootDir()` | 获取当前/根目录 |

### 5.6 MCPTool 接口

所有工具必须实现的接口。

[文件路径](file:///workspace/app/src/main/java/com/mcp_run/tools/MCPTool.java)

```java
public interface MCPTool {
    String getName();           // 工具名称
    String getDescription();    // 工具描述
    JSONObject getInputSchema(); // 参数的 JSON Schema
    JSONObject execute(Context context, JSONObject args); // 执行工具
}
```

### 5.7 主要工具类

| 工具类 | 注册名 | 核心功能 |
|--------|--------|---------|
| ShellTool | `shell` | 执行 Shell 命令，支持超时和工作目录 |
| SystemControlTool | `system_control` | 音量调节、WiFi 开关、锁屏、按键模拟 |
| DeviceInfoTool | `device_info` | 获取 Android 设备硬件/系统/存储信息 |
| AppManagerTool | `app_manager` | 列出/启动/停止/卸载应用，获取前台 Activity |
| HttpTool | `http_get/post/put/delete/json/download_text/download_file` | HTTP 请求代理 |
| ScreenshotTool | `screenshot` | 截取屏幕保存为 PNG |
| ClipboardTool | `clipboard` | 读写系统剪贴板 |
| NotificationTool | `send_notification` | 发送系统通知 |
| ImageTool | `image_info/resize/convert/crop/rotate/to_base64/base64_to_image` | 图片处理 |
| ScriptTool | `mcp_javascript` / `mcp_python` | JavaScript/Python 脚本执行 |
| FileSystemTool | `pwd/cd/ls/tree/find/grep/mkdir/copy/delete/edit` 等 17 个 | 文件系统操作 |
| FileContentTool | `read/write/append/head/tail/read_lines/batch_read` 等 10 个 | 文件内容读写 |
| JsonFormatTool | `json_format` | JSON 格式化/压缩/验证 |
| TextConvertTool | `text_convert` | 文本大小写转换、去空白、统计 |
| InfoTool | `health/service_info/history/get_time_info` 等 9 个 | 服务信息和帮助 |
| BatchOpsTool | `batch_ops` | 批量执行多个工具 |

---

## 6. 依赖关系

### 6.1 Gradle 依赖

```groovy
dependencies {
    implementation 'androidx.core:core'           // AndroidX 核心库
    implementation 'androidx.appcompat:appcompat'  // 向后兼容
    implementation 'com.google.android.material:material'  // Material Design 组件
    implementation 'androidx.constraintlayout:constraintlayout' // 约束布局
    implementation 'androidx.drawerlayout:drawerlayout' // 侧滑菜单
}
```

### 6.2 类依赖图

```
MainActivity
  ├── MCPService (启动/停止)
  ├── ToolRegistry (获取工具数)
  └── ScreenshotTool (设置根视图)

MCPService
  └── MCPHttpServer (启动/停止 HTTP 服务器)

MCPHttpServer
  └── ToolRegistry (注册、查找、执行工具)

ToolRegistry
  ├── FileSession (文件会话)
  └── MCPTool[] (所有工具实现)
      ├── FileSystemTool ── FileSession
      ├── FileContentTool ── FileSession
      ├── ShellTool
      ├── SystemControlTool
      ├── SysTool
      ├── DeviceInfoTool
      ├── ScreenshotTool
      ├── ImageTool
      ├── ImageInfoTool
      ├── AppManagerTool
      ├── ApkInstallTool
      ├── ScriptTool
      ├── PythonExecuteTool
      ├── ClipboardTool
      ├── NotificationTool
      ├── HttpTool
      ├── FileReadTool
      ├── FileWriteTool
      ├── FileDeleteTool
      ├── FileMoveTool
      ├── FileListTool
      ├── JsonFormatTool
      ├── TextConvertTool
      ├── InfoTool
      ├── BatchOpsTool
      └── ActionTool (通用工具构建器)
```

---

## 7. 项目运行方式

### 7.1 构建

```bash
# 使用 Gradle 构建
./gradlew assembleDebug

# 生成的 APK 位于
# app/build/outputs/apk/debug/app-debug.apk
```

### 7.2 安装与运行

1. 将 APK 安装到 Android 设备上
2. 打开应用，首次启动会自动请求必要权限
3. 配置工作区路径（默认 `/storage/emulated/0/`）
4. 设置端口（默认 `1145`）
5. 点击"启动服务器"

### 7.3 与 MCP 客户端连接

服务器启动后，在 MCP 客户端配置：

```json
{
  "mcpServers": {
    "android-device": {
      "url": "http://127.0.0.1:1145/mcp",
      "transport": "streamable-http"
    }
  }
}
```

### 7.4 直接 API 调用示例

```bash
# 初始化
curl -X POST http://127.0.0.1:1145/mcp \
  -H "Content-Type: application/json" \
  -H "MCP-Version: 2025-03-26" \
  -d '{"jsonrpc":"2.0","method":"initialize","id":1}'

# 列出工具
curl -X POST http://127.0.0.1:1145/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":2}'

# 调用工具（获取设备信息）
curl -X POST http://127.0.0.1:1145/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"device_info","arguments":{}},"id":3}'

# 查看状态
curl http://127.0.0.1:1145/status

# 查看工具列表页面
curl http://127.0.0.1:1145/tools
```

---

## 8. 权限清单

| 权限 | 用途 |
|------|------|
| `INTERNET` | HTTP 服务器网络通信 |
| `ACCESS_NETWORK_STATE` | 网络状态检测 |
| `ACCESS_WIFI_STATE` | WiFi 状态检测 |
| `FOREGROUND_SERVICE` | 前台服务运行 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 前台服务类型声明 |
| `READ_EXTERNAL_STORAGE` (≤32) | 读取外部存储（旧版） |
| `WRITE_EXTERNAL_STORAGE` (≤29) | 写入外部存储（旧版） |
| `READ_MEDIA_IMAGES` | 读取媒体图片 (Android 13+) |
| `READ_MEDIA_VIDEO` | 读取媒体视频 (Android 13+) |
| `READ_MEDIA_AUDIO` | 读取媒体音频 (Android 13+) |
| `POST_NOTIFICATIONS` | 发送通知 (Android 13+) |
| `VIBRATE` | 振动反馈 |
| `RECEIVE_BOOT_COMPLETED` | 开机广播（声明但未使用） |

---

## 9. MCP 协议工具列表

### 9.1 文件操作 (📁)

| 工具名 | 描述 |
|--------|------|
| `pwd` | 查看当前工作目录 |
| `cd` | 切换工作目录 |
| `set_root` | 设置工作根目录 |
| `exists` | 检查路径是否存在 |
| `stat` | 查看文件/目录详情 |
| `ls` | 列出目录内容 |
| `list_all` | 列出全部内容（含隐藏） |
| `tree` | 树形结构展示目录 |
| `find` | 按名称搜索文件 |
| `grep` | 在文件中搜索文本 |
| `mkdir` | 创建目录 |
| `touch` | 创建空文件/更新时间戳 |
| `empty` | 清空文件内容 |
| `copy` | 复制文件/目录 |
| `rename` | 重命名/移动 |
| `delete` | 删除文件/目录 |
| `edit` | 文本替换 |
| `read` | 读取文本文件 |
| `head` | 读取文件开头 N 行 |
| `tail` | 读取文件末尾 N 行 |
| `read_lines` | 读取指定行范围 |
| `batch_read` | 批量读取多个文件 |
| `read_base64` | Base64 读取二进制文件 |
| `write` | 写入文本文件 |
| `append` | 追加写入 |
| `write_base64` | Base64 写入二进制文件 |
| `compare_files` | 对比两个文件差异 |

### 9.2 系统管理 (⚙️)

| 工具名 | 描述 |
|--------|------|
| `shell` | 执行 Shell 命令 |
| `system_control` | 音量/WiFi/锁屏/按键等 |
| `shizuku` | 查看 Shizuku 状态 |
| `shizuku_shell` | Shizuku 高权限 Shell |
| `battery` | 电池状态信息 |
| `battery_fix` | 电池优化设置 |

### 9.3 设备信息 (📱)

| 工具名 | 描述 |
|--------|------|
| `device_info` | 设备硬件/系统/存储信息 |
| `screenshot` | 截取屏幕 |
| `image_info` | 图片基本信息 |
| `image_resize` | 缩放图片 |
| `image_convert` | 转换图片格式 |
| `image_crop` | 裁剪图片 |
| `image_rotate` | 旋转图片 |
| `image_to_base64` | 图片转 Base64 |
| `base64_to_image` | Base64 转图片 |

### 9.4 应用管理 (📦)

| 工具名 | 描述 |
|--------|------|
| `app_manager` | 列出/启动/停止/卸载应用 |
| `install_apk` | 安装 APK 文件 |

### 9.5 脚本执行 (🐍)

| 工具名 | 描述 |
|--------|------|
| `mcp_javascript` | 执行 JavaScript 代码 |
| `mcp_python` | 执行 Python 代码 |
| `execute_python` | Python 脚本执行（增强版） |

### 9.6 通讯交互 (💬)

| 工具名 | 描述 |
|--------|------|
| `clipboard` | 读写系统剪贴板 |
| `send_notification` | 发送系统通知 |

### 9.7 网络请求 (🌐)

| 工具名 | 描述 |
|--------|------|
| `http_get` | HTTP GET 请求 |
| `http_post` | HTTP POST 请求 |
| `http_put` | HTTP PUT 请求 |
| `http_delete` | HTTP DELETE 请求 |
| `http_json` | HTTP 请求并自动解析 JSON |
| `download_text` | 下载文本文件 |
| `download_file` | 下载二进制文件 |

### 9.8 实用工具 (🧰)

| 工具名 | 描述 |
|--------|------|
| `json_format` | JSON 格式化/压缩/验证 |
| `text_convert` | 文本格式转换 |
| `get_time_info` | 当前时间信息 |
| `health` | 服务健康检查 |
| `service_info` | 服务详情 |
| `history` | 运行历史 |
| `clear_log` | 清空日志 |
| `tool_help` | 工具帮助总览 |
| `script_help` | 脚本帮助 |
| `file_help` | 文件工具帮助 |
| `system_help` | 系统工具帮助 |
| `batch_ops` | 批量操作 |

---

> **文档生成时间**: 2026-07-24  
> **分析项目**: MCP_Run v1.1  
> **安全审计**: 通过 - 无后门、无病毒、无恶意代码