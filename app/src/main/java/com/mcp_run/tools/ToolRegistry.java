package com.mcp_run.tools;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表 - 管理所有可用的MCP工具
 * 18+ 个工具，8个分类
 */
public class ToolRegistry {
    private final Map<String, MCPTool> tools = new HashMap<>();
    private final Map<String, String> toolCategories = new HashMap<>();
    private final FileSession session;

    public static final String CAT_FILE = "文件操作";
    public static final String CAT_SYSTEM = "系统管理";
    public static final String CAT_DEVICE = "设备信息";
    public static final String CAT_APP = "应用管理";
    public static final String CAT_SCRIPT = "脚本执行";
    public static final String CAT_COMMUNICATION = "通讯交互";
    public static final String CAT_NETWORK = "网络请求";
    public static final String CAT_UTILITY = "实用工具";

    public ToolRegistry(Context context) {
        this.session = new FileSession();
        registerAll(context);
    }

    public FileSession getSession() { return session; }

    private void registerAll(Context context) {
        // ====== 文件系统管理 ======
        register(new FileSystemTool("pwd", session), CAT_FILE);
        register(new FileSystemTool("cd", session), CAT_FILE);
        register(new FileSystemTool("set_root", session), CAT_FILE);
        register(new FileSystemTool("exists", session), CAT_FILE);
        register(new FileSystemTool("stat", session), CAT_FILE);
        register(new FileSystemTool("ls", session), CAT_FILE);
        register(new FileSystemTool("list_all", session), CAT_FILE);
        register(new FileSystemTool("tree", session), CAT_FILE);
        register(new FileSystemTool("find", session), CAT_FILE);
        register(new FileSystemTool("grep", session), CAT_FILE);
        register(new FileSystemTool("mkdir", session), CAT_FILE);
        register(new FileSystemTool("touch", session), CAT_FILE);
        register(new FileSystemTool("empty", session), CAT_FILE);
        register(new FileSystemTool("copy", session), CAT_FILE);
        register(new FileSystemTool("rename", session), CAT_FILE);
        register(new FileSystemTool("delete", session), CAT_FILE);
        register(new FileSystemTool("edit", session), CAT_FILE);

        // ====== 文件内容读写 ======
        register(new FileContentTool("read", session), CAT_FILE);
        register(new FileContentTool("head", session), CAT_FILE);
        register(new FileContentTool("tail", session), CAT_FILE);
        register(new FileContentTool("read_lines", session), CAT_FILE);
        register(new FileContentTool("batch_read", session), CAT_FILE);
        register(new FileContentTool("read_base64", session), CAT_FILE);
        register(new FileContentTool("write", session), CAT_FILE);
        register(new FileContentTool("append", session), CAT_FILE);
        register(new FileContentTool("write_base64", session), CAT_FILE);
        register(new FileContentTool("compare_files", session), CAT_FILE);

        // ====== 系统管理 ======
        register(new ShellTool(), CAT_SYSTEM);
        register(new SystemControlTool(), CAT_SYSTEM);
        register(new SysTool("shizuku"), CAT_SYSTEM);
        register(new SysTool("shizuku_shell"), CAT_SYSTEM);
        register(new SysTool("battery"), CAT_SYSTEM);
        register(new SysTool("battery_fix"), CAT_SYSTEM);

        // ====== 设备信息 ======
        register(new DeviceInfoTool(), CAT_DEVICE);
        register(new ScreenshotTool(), CAT_DEVICE);
        register(new ImageTool("image_info"), CAT_DEVICE);
        register(new ImageTool("image_resize"), CAT_DEVICE);
        register(new ImageTool("image_convert"), CAT_DEVICE);
        register(new ImageTool("image_crop"), CAT_DEVICE);
        register(new ImageTool("image_rotate"), CAT_DEVICE);
        register(new ImageTool("image_to_base64"), CAT_DEVICE);
        register(new ImageTool("base64_to_image"), CAT_DEVICE);

        // ====== 应用管理 ======
        register(new AppManagerTool(), CAT_APP);
        register(new ApkInstallTool(), CAT_APP);

        // ====== 脚本执行 ======
        register(new ScriptTool("mcp_javascript"), CAT_SCRIPT);
        register(new ScriptTool("mcp_python"), CAT_SCRIPT);

        // ====== 通讯交互 ======
        register(new ClipboardTool(), CAT_COMMUNICATION);
        register(new NotificationTool(), CAT_COMMUNICATION);

        // ====== 网络请求 ======
        register(new HttpTool("http_get"), CAT_NETWORK);
        register(new HttpTool("http_post"), CAT_NETWORK);
        register(new HttpTool("http_put"), CAT_NETWORK);
        register(new HttpTool("http_delete"), CAT_NETWORK);
        register(new HttpTool("http_json"), CAT_NETWORK);
        register(new HttpTool("download_text"), CAT_NETWORK);
        register(new HttpTool("download_file"), CAT_NETWORK);

        // ====== 实用工具 ======
        register(new JsonFormatTool(), CAT_UTILITY);
        register(new TextConvertTool(), CAT_UTILITY);
        register(new InfoTool("get_time_info"), CAT_UTILITY);
        register(new InfoTool("health"), CAT_UTILITY);
        register(new InfoTool("service_info"), CAT_UTILITY);
        register(new InfoTool("history"), CAT_UTILITY);
        register(new InfoTool("clear_log"), CAT_UTILITY);
        register(new InfoTool("tool_help"), CAT_UTILITY);
        register(new InfoTool("script_help"), CAT_UTILITY);
        register(new InfoTool("file_help"), CAT_UTILITY);
        register(new InfoTool("system_help"), CAT_UTILITY);
        register(new BatchOpsTool(), CAT_UTILITY);
    }

    private void register(MCPTool tool, String category) {
        tools.put(tool.getName(), tool);
        toolCategories.put(tool.getName(), category);
    }

    public MCPTool getTool(String name) { return tools.get(name); }
    public List<MCPTool> getAllTools() { return new ArrayList<>(tools.values()); }
    public int getToolCount() { return tools.size(); }

    public String getCategory(String toolName) {
        return toolCategories.getOrDefault(toolName, "其他");
    }

    public List<String> getAllCategories() {
        List<String> cats = new ArrayList<>();
        cats.add(CAT_FILE); cats.add(CAT_SYSTEM); cats.add(CAT_DEVICE);
        cats.add(CAT_APP); cats.add(CAT_SCRIPT); cats.add(CAT_COMMUNICATION);
        cats.add(CAT_NETWORK); cats.add(CAT_UTILITY);
        return cats;
    }

    public List<MCPTool> getToolsByCategory(String category) {
        List<MCPTool> result = new ArrayList<>();
        for (MCPTool tool : tools.values()) {
            if (category.equals(toolCategories.get(tool.getName()))) result.add(tool);
        }
        return result;
    }

    public static String getCategoryIcon(String category) {
        switch (category) {
            case CAT_FILE: return "📁";
            case CAT_SYSTEM: return "⚙️";
            case CAT_DEVICE: return "📱";
            case CAT_APP: return "📦";
            case CAT_SCRIPT: return "🐍";
            case CAT_COMMUNICATION: return "💬";
            case CAT_NETWORK: return "🌐";
            case CAT_UTILITY: return "🧰";
            default: return "🔧";
        }
    }

    public static String getToolIcon(String toolName) {
        switch (toolName) {
            case "pwd": return "📍";
            case "cd": case "set_root": return "📂";
            case "exists": return "🔍";
            case "stat": return "📊";
            case "ls": case "list_all": return "📋";
            case "tree": return "🌳";
            case "find": return "🔎";
            case "grep": return "📑";
            case "mkdir": return "📁";
            case "touch": return "📄";
            case "empty": return "🗑️";
            case "copy": return "📋";
            case "rename": return "✏️";
            case "delete": return "❌";
            case "edit": return "🔧";
            case "read": return "📖";
            case "head": case "tail": return "📃";
            case "read_lines": return "📏";
            case "batch_read": return "📚";
            case "read_base64": return "🔢";
            case "write": return "✍️";
            case "append": return "➕";
            case "write_base64": return "🔣";
            case "compare_files": return "⚖️";
            case "shell": return "💻";
            case "shizuku": return "🔐";
            case "shizuku_shell": return "⚡";
            case "battery": return "🔋";
            case "battery_fix": return "💡";
            case "device_info": return "ℹ️";
            case "screenshot": return "📸";
            case "image_info": return "🖼️";
            case "image_resize": return "📐";
            case "image_convert": return "🔄";
            case "image_crop": return "✂️";
            case "image_rotate": return "↩️";
            case "image_to_base64": return "🔡";
            case "base64_to_image": return "🖼️";
            case "app_manager": return "📱";
            case "install_apk": return "📲";
            case "mcp_javascript": return "🟨";
            case "mcp_python": return "🐍";
            case "clipboard": return "📋";
            case "send_notification": return "🔔";
            case "http_get": return "🌐";
            case "http_post": return "📤";
            case "http_put": return "📝";
            case "http_delete": return "🗑️";
            case "http_json": return "📊";
            case "download_text": return "📄";
            case "download_file": return "💾";
            case "json_format": return "📝";
            case "text_convert": return "🔤";
            case "get_time_info": return "🕐";
            case "health": return "💚";
            case "service_info": return "📋";
            case "history": return "📜";
            case "clear_log": return "🧹";
            case "tool_help": return "❓";
            case "script_help": return "📖";
            case "file_help": return "📁";
            case "system_help": return "⚙️";
            case "batch_ops": return "📦";
            default: return "🔧";
        }
    }
}