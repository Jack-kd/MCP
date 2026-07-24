package com.mcp_run.tools;

import android.app.ActivityManager;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * 进程管理工具
 * 支持 ps / pidof / kill / top
 */
public class ProcessTool implements MCPTool {
    private final String action;

    public ProcessTool(String action) {
        this.action = action;
    }

    @Override
    public String getName() {
        return action;
    }

    @Override
    public String getDescription() {
        switch (action) {
            case "ps": return "列出当前运行中的进程信息，支持按名称过滤。";
            case "pidof": return "根据进程名查找PID。";
            case "kill": return "终止指定PID的进程。";
            case "top": return "获取系统资源使用排行（CPU/内存），类似top命令。";
            default: return "进程管理操作";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            switch (action) {
                case "ps": {
                    JSONObject filterProp = new JSONObject();
                    filterProp.put("type", "string");
                    filterProp.put("description", "按进程名过滤（可选），不填则列出所有进程");
                    props.put("filter", filterProp);

                    JSONObject limitProp = new JSONObject();
                    limitProp.put("type", "integer");
                    limitProp.put("description", "最多返回条数，默认50");
                    limitProp.put("default", 50);
                    props.put("limit", limitProp);
                    break;
                }
                case "pidof": {
                    JSONObject nameProp = new JSONObject();
                    nameProp.put("type", "string");
                    nameProp.put("description", "要查找的进程名，如 com.mcp_run");
                    props.put("process_name", nameProp);
                    break;
                }
                case "kill": {
                    JSONObject pidProp = new JSONObject();
                    pidProp.put("type", "integer");
                    pidProp.put("description", "要终止的进程PID");
                    props.put("pid", pidProp);

                    JSONObject sigProp = new JSONObject();
                    sigProp.put("type", "integer");
                    sigProp.put("description", "信号编号，默认9（SIGKILL），可选15（SIGTERM）");
                    sigProp.put("default", 9);
                    props.put("signal", sigProp);
                    break;
                }
                case "top": {
                    JSONObject nProp = new JSONObject();
                    nProp.put("type", "integer");
                    nProp.put("description", "返回前N条进程，默认10");
                    nProp.put("default", 10);
                    props.put("count", nProp);
                    break;
                }
            }

            schema.put("properties", props);
            JSONArray required = new JSONArray();
            if ("pidof".equals(action)) required.put("process_name");
            if ("kill".equals(action)) required.put("pid");
            schema.put("required", required);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        JSONObject result = new JSONObject();

        switch (action) {
            case "ps": {
                String filter = args.optString("filter", null);
                int limit = args.optInt("limit", 50);

                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();

                JSONArray procList = new JSONArray();
                int count = 0;
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo proc : processes) {
                        if (filter != null && !proc.processName.toLowerCase().contains(filter.toLowerCase())) {
                            continue;
                        }
                        if (count >= limit) break;

                        JSONObject p = new JSONObject();
                        p.put("pid", proc.pid);
                        p.put("process_name", proc.processName);
                        p.put("uid", proc.uid);
                        p.put("importance", proc.importance);
                        p.put("importance_reason", proc.importanceReasonCode);
                        procList.put(p);
                        count++;
                    }
                }
                result.put("processes", procList);
                result.put("count", procList.length());
                result.put("success", true);
                break;
            }
            case "pidof": {
                String processName = args.getString("process_name");
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();

                JSONArray pids = new JSONArray();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo proc : processes) {
                        if (proc.processName.equals(processName) || proc.processName.contains(processName)) {
                            pids.put(proc.pid);
                        }
                    }
                }
                result.put("pids", pids);
                result.put("count", pids.length());
                result.put("success", true);
                break;
            }
            case "kill": {
                int pid = args.getInt("pid");
                int signal = args.optInt("signal", 9);

                String sigStr = signal == 9 ? "-9" : "-15";
                java.lang.Process p = Runtime.getRuntime().exec(new String[]{"kill", sigStr, String.valueOf(pid)});
                p.waitFor();

                result.put("pid", pid);
                result.put("signal", signal);
                result.put("success", p.exitValue() == 0);
                result.put("message", p.exitValue() == 0 ? "进程已终止" : "终止失败");
                break;
            }
            case "top": {
                int count = args.optInt("count", 10);
                java.lang.Process p = Runtime.getRuntime().exec(new String[]{"top", "-b", "-n", "1", "-o", "%CPU", "-o", "RES"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                JSONArray topList = new JSONArray();
                String line;
                int lineCount = 0;
                boolean inProcessList = false;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        inProcessList = true;
                        continue;
                    }
                    if (inProcessList && lineCount < count) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 8) {
                            JSONObject proc = new JSONObject();
                            proc.put("pid", parts[0]);
                            proc.put("user", parts[1]);
                            proc.put("cpu", parts[parts.length - 4]);
                            proc.put("mem", parts[parts.length - 2]);
                            proc.put("name", parts[parts.length - 1]);
                            topList.put(proc);
                            lineCount++;
                        }
                    }
                }
                reader.close();
                result.put("processes", topList);
                result.put("count", topList.length());
                result.put("success", true);
                break;
            }
        }
        return result;
    }
}