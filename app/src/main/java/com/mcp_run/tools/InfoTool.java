package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 信息工具集 - health, service_info, history, clear_log, get_time_info, 各类help
 */
public class InfoTool implements MCPTool {
    private final String toolName;
    private static final java.util.List<String> history = new java.util.ArrayList<>();
    private static final java.util.List<String> log = new java.util.ArrayList<>();

    public static void addHistory(String entry) { history.add("[" + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()) + "] " + entry); }
    public static void addLog(String entry) { log.add(entry); }
    public static void clearLog() { log.clear(); }

    public InfoTool(String toolName) { this.toolName = toolName; }

    @Override public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "health": return "查看MCP服务健康状态，检查服务器是否正常运行";
            case "service_info": return "查看服务详情信息，包括版本、工具数、运行时间等";
            case "history": return "查看工具运行历史记录";
            case "clear_log": return "清空运行日志记录";
            case "get_time_info": return "获取当前本地日期时间、时区、ISO时间字符串、时间戳等信息";
            case "tool_help": return "查看工具帮助总览，列出所有可用工具";
            case "script_help": return "查看脚本/JS桥执行帮助";
            case "file_help": return "查看文件类工具帮助说明";
            case "system_help": return "查看系统/高风险工具帮助说明";
            default: return "信息工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", new JSONObject());
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "health": return health();
            case "service_info": return serviceInfo();
            case "history": return getHistory();
            case "clear_log": return execClearLog();
            case "get_time_info": return timeInfo();
            case "tool_help": return help("tool");
            case "script_help": return help("script");
            case "file_help": return help("file");
            case "system_help": return help("system");
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    private JSONObject health() throws Exception {
        JSONObject r = new JSONObject();
        r.put("status", "healthy");
        r.put("server", "MCP_Run");
        r.put("version", "1.1.0");
        r.put("uptime", "running");
        return r;
    }

    private JSONObject serviceInfo() throws Exception {
        JSONObject r = new JSONObject();
        r.put("name", "MCP_Run Android Server");
        r.put("version", "1.1.0");
        r.put("protocol", "Streamable HTTP (MCP 2025-03-26)");
        r.put("bind_address", "127.0.0.1");
        r.put("tools_count", "18+");
        r.put("features", new JSONArray()
                .put("文件操作").put("系统管理").put("设备信息")
                .put("应用管理").put("脚本执行").put("通讯交互")
                .put("网络请求").put("实用工具").put("图片处理"));
        return r;
    }

    private JSONObject getHistory() throws Exception {
        JSONArray arr = new JSONArray();
        for (String entry : history) arr.put(entry);
        JSONObject r = new JSONObject();
        r.put("total", history.size());
        r.put("entries", arr);
        return r;
    }

    private JSONObject execClearLog() throws Exception {
        InfoTool.clearLog();
        history.clear();
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("message", "日志已清空");
        return r;
    }

    private JSONObject timeInfo() throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
        JSONObject r = new JSONObject();
        r.put("timestamp", System.currentTimeMillis());
        r.put("iso_string", sdf.format(new Date()));
        r.put("timezone", TimeZone.getDefault().getDisplayName());
        r.put("timezone_id", TimeZone.getDefault().getID());
        r.put("year", java.util.Calendar.getInstance().get(java.util.Calendar.YEAR));
        r.put("month", java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1);
        r.put("day", java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH));
        r.put("hour", java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY));
        r.put("minute", java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE));
        r.put("second", java.util.Calendar.getInstance().get(java.util.Calendar.SECOND));
        r.put("weekday", getWeekday(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)));
        return r;
    }

    private String getWeekday(int day) {
        String[] days = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return day >= 1 && day <= 7 ? days[day - 1] : "未知";
    }

    private JSONObject help(String type) throws Exception {
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case "tool":
                sb.append("MCP_Run 工具帮助总览\n\n");
                sb.append("📁 文件操作: pwd, cd, set_root, exists, stat, ls, list_all, tree, find, grep, mkdir, touch, empty, copy, rename, delete, edit\n");
                sb.append("📄 文件内容: read, head, tail, read_lines, batch_read, read_base64, write, append, write_base64, compare_files\n");
                sb.append("⚙️ 系统管理: shell, system_control, shizuku, shizuku_shell, battery, battery_fix\n");
                sb.append("📱 设备信息: device_info, screenshot, image_info, image_resize, image_convert, image_crop, image_rotate, image_to_base64, base64_to_image\n");
                sb.append("📦 应用管理: app_manager, install_apk\n");
                sb.append("🐍 脚本执行: execute_python, mcp_javascript\n");
                sb.append("💬 通讯交互: clipboard, send_notification\n");
                sb.append("🌐 网络请求: http_get, http_post, http_put, http_delete, http_json, download_text, download_file\n");
                sb.append("🧰 实用工具: json_format, text_convert, get_time_info, health, service_info, history, clear_log\n");
                break;
            case "script":
                sb.append("脚本执行帮助:\n\n");
                sb.append("execute_python - 执行Python脚本或代码片段\n");
                sb.append("  - 参数: action(execute_file|execute_code|find_python)\n");
                sb.append("  - 需要设备上安装Python环境\n\n");
                sb.append("mcp_javascript - 执行JavaScript代码\n");
                sb.append("  - 使用内置QuickJS引擎\n");
                sb.append("  - 每次执行独立的隔离环境\n");
                break;
            case "file":
                sb.append("文件操作帮助:\n\n");
                sb.append("路径说明:\n");
                sb.append("  - 支持绝对路径(如 /storage/emulated/0/)\n");
                sb.append("  - 支持相对路径(相对于当前工作目录)\n");
                sb.append("  - 可通过 pwd/cd/set_root 管理工作目录\n\n");
                sb.append("常用工具:\n");
                sb.append("  read/write - 读写文本文件\n");
                sb.append("  ls/tree - 浏览目录结构\n");
                sb.append("  find/grep - 搜索文件和内容\n");
                sb.append("  copy/rename/delete - 管理文件\n");
                sb.append("  edit - 替换文件中的文本\n");
                break;
            case "system":
                sb.append("系统/高风险工具帮助:\n\n");
                sb.append("⚠️ 以下工具可能影响系统稳定性:\n\n");
                sb.append("shell - 执行任意Shell命令\n");
                sb.append("system_control - 音量/WiFi/锁屏/按键/电池等\n");
                sb.append("shizuku_shell - 使用Shizuku高权限执行命令\n");
                sb.append("install_apk - 安装APK文件\n");
                sb.append("battery_fix - 修改电池优化设置\n\n");
                sb.append("请谨慎使用以上工具！");
                break;
        }
        JSONObject r = new JSONObject();
        r.put("help", sb.toString());
        r.put("type", type);
        return r;
    }
}