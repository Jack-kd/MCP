package com.mcp_run.tools;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 系统辅助工具集 - shizuku, shizuku_shell, battery, battery_fix
 */
public class SysTool implements MCPTool {
    private final String toolName;

    public SysTool(String toolName) { this.toolName = toolName; }

    @Override public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "shizuku": return "查看Shizuku权限状态，判断是否已授权";
            case "shizuku_shell": return "使用Shizuku权限执行高权限shell命令";
            case "battery": return "查看电池状态和保活信息（电量、温度、充电状态等）";
            case "battery_fix": return "尝试将应用设为省电无限制模式，防止后台被杀";
            default: return "系统工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            switch (toolName) {
                case "shizuku": break;
                case "shizuku_shell": {
                    JSONObject c = new JSONObject(); c.put("type", "string"); c.put("description", "要执行的命令"); props.put("cmd", c);
                    schema.put("required", new JSONArray().put("cmd")); break;
                }
                case "battery": break;
                case "battery_fix": {
                    JSONObject m = new JSONObject(); m.put("type", "string"); m.put("description", "模式: system/shizuku"); m.put("default", "system"); props.put("mode", m);
                    schema.put("required", new JSONArray().put("mode")); break;
                }
            }
            schema.put("properties", props);
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "shizuku": return shizukuStatus(context);
            case "shizuku_shell": return shizukuShell(args);
            case "battery": return batteryInfo(context);
            case "battery_fix": return batteryFix(context, args);
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    private JSONObject shizukuStatus(Context context) throws Exception {
        JSONObject r = new JSONObject();
        try {
            Class<?> shizuku = Class.forName("moe.shizuku.api.ShizukuBinderWrapper");
            r.put("shizuku_available", true);
            r.put("status", "Shizuku API 可用");
        } catch (ClassNotFoundException e) {
            r.put("shizuku_available", false);
            r.put("status", "Shizuku 未安装");
        }
        // 检查是否有root
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "su -c 'echo root_ok' 2>/dev/null"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            r.put("root_available", "root_ok".equals(line));
            p.waitFor();
        } catch (Exception e) {
            r.put("root_available", false);
        }
        return r;
    }

    private JSONObject shizukuShell(JSONObject args) throws Exception {
        String cmd = args.getString("cmd");
        String output = execShell(cmd);
        JSONObject r = new JSONObject();
        r.put("command", cmd);
        r.put("output", output.trim());
        r.put("success", true);
        return r;
    }

    private JSONObject batteryInfo(Context context) throws Exception {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battery = context.registerReceiver(null, ifilter);
        JSONObject r = new JSONObject();
        if (battery != null) {
            int level = battery.getIntExtra("level", -1);
            int scale = battery.getIntExtra("scale", -1);
            int temp = battery.getIntExtra("temperature", -1);
            int voltage = battery.getIntExtra("voltage", -1);
            int status = battery.getIntExtra("status", -1);
            int plugged = battery.getIntExtra("plugged", -1);
            r.put("level", level);
            r.put("scale", scale);
            r.put("percentage", scale > 0 ? (level * 100 / scale) : -1);
            r.put("temperature_celsius", temp > 0 ? (temp / 10.0) : -1);
            r.put("voltage_mv", voltage);
            String[] statuses = {"unknown", "unknown", "charging", "full", "not_charging", "discharging"};
            r.put("status", status >= 0 && status < statuses.length ? statuses[status] : "unknown");
            String[] plugs = {"unknown", "ac", "usb", "unknown", "wireless"};
            r.put("plugged", plugged >= 0 && plugged < plugs.length ? plugs[plugged] : "unknown");
        }
        return r;
    }

    private JSONObject batteryFix(Context context, JSONObject args) throws Exception {
        String mode = args.optString("mode", "system");
        JSONObject r = new JSONObject();
        if ("system".equals(mode)) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                r.put("success", true);
                r.put("message", "已打开电池优化设置页面，请手动将MCP Tool设为「无限制」");
            } catch (Exception e) {
                r.put("success", false);
                r.put("message", "打开设置失败: " + e.getMessage());
            }
        } else {
            String output = execShell("settings put global power_save_disabled 1");
            r.put("success", true);
            r.put("message", "已尝试通过shell设置");
            r.put("output", output.trim());
        }
        return r;
    }

    private String execShell(String command) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line; while ((line = br.readLine()) != null) out.append(line).append("\n");
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            String line; while ((line = br.readLine()) != null) out.append(line).append("\n");
        }
        p.waitFor();
        return out.toString();
    }
}