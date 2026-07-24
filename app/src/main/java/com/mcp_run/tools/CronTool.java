package com.mcp_run.tools;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 定时任务工具
 * 支持 cron_add / cron_list / cron_remove
 * 使用 ScheduledExecutorService 实现定时执行
 */
public class CronTool implements MCPTool {
    private final String action;

    private static final String PREFS_NAME = "mcp_cron_jobs";
    private static final Map<Integer, ScheduledExecutorService> activeJobs = new java.util.concurrent.ConcurrentHashMap<>();
    private static final AtomicInteger jobIdCounter = new AtomicInteger(1);

    public CronTool(String action) {
        this.action = action;
    }

    @Override
    public String getName() {
        return "cron_" + action;
    }

    @Override
    public String getDescription() {
        switch (action) {
            case "add": return "添加定时任务。支持按秒/分钟/小时/天间隔执行Shell命令。注意：应用关闭后任务会停止。";
            case "list": return "列出所有已添加的定时任务及其状态。";
            case "remove": return "移除指定的定时任务。";
            default: return "定时任务管理";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            if ("add".equals(action)) {
                JSONObject cmdProp = new JSONObject();
                cmdProp.put("type", "string");
                cmdProp.put("description", "要执行的Shell命令");
                props.put("command", cmdProp);

                JSONObject intervalProp = new JSONObject();
                intervalProp.put("type", "integer");
                intervalProp.put("description", "执行间隔（秒），最小5秒");
                intervalProp.put("default", 60);
                props.put("interval_seconds", intervalProp);

                JSONObject nameProp = new JSONObject();
                nameProp.put("type", "string");
                nameProp.put("description", "任务名称（可选，用于标识）");
                props.put("name", nameProp);

                JSONObject onceProp = new JSONObject();
                onceProp.put("type", "boolean");
                onceProp.put("description", "是否仅执行一次，默认false（重复执行）");
                onceProp.put("default", false);
                props.put("once", onceProp);
            } else if ("remove".equals(action)) {
                JSONObject idProp = new JSONObject();
                idProp.put("type", "integer");
                idProp.put("description", "要移除的任务ID（可通过cron_list获取）");
                props.put("job_id", idProp);
            }

            schema.put("properties", props);
            JSONArray required = new JSONArray();
            if ("add".equals(action)) required.put("command");
            if ("remove".equals(action)) required.put("job_id");
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
            case "add": {
                String command = args.getString("command");
                int intervalSec = args.optInt("interval_seconds", 60);
                boolean once = args.optBoolean("once", false);
                String name = args.optString("name", "cron_" + System.currentTimeMillis());

                if (intervalSec < 5) throw new Exception("间隔不能小于5秒");

                int jobId = jobIdCounter.getAndIncrement();
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

                Runnable task = () -> {
                    try {
                        java.lang.Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                        p.waitFor();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                };

                if (once) {
                    scheduler.schedule(task, intervalSec, TimeUnit.SECONDS);
                } else {
                    scheduler.scheduleAtFixedRate(task, intervalSec, intervalSec, TimeUnit.SECONDS);
                }

                activeJobs.put(jobId, scheduler);

                // 保存任务信息
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                JSONObject jobInfo = new JSONObject();
                jobInfo.put("id", jobId);
                jobInfo.put("name", name);
                jobInfo.put("command", command);
                jobInfo.put("interval_seconds", intervalSec);
                jobInfo.put("once", once);
                jobInfo.put("created_at", System.currentTimeMillis());
                prefs.edit().putString("job_" + jobId, jobInfo.toString()).apply();

                result.put("job_id", jobId);
                result.put("name", name);
                result.put("command", command);
                result.put("interval_seconds", intervalSec);
                result.put("once", once);
                result.put("success", true);
                result.put("message", "定时任务已添加");
                break;
            }
            case "list": {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                Map<String, ?> all = prefs.getAll();
                JSONArray jobs = new JSONArray();

                for (Map.Entry<String, ?> entry : all.entrySet()) {
                    if (entry.getKey().startsWith("job_")) {
                        try {
                            JSONObject job = new JSONObject((String) entry.getValue());
                            int jobId = job.getInt("id");
                            job.put("active", activeJobs.containsKey(jobId));
                            jobs.put(job);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                result.put("jobs", jobs);
                result.put("count", jobs.length());
                result.put("active_count", activeJobs.size());
                result.put("success", true);
                break;
            }
            case "remove": {
                int jobId = args.getInt("job_id");

                if (activeJobs.containsKey(jobId)) {
                    ScheduledExecutorService scheduler = activeJobs.remove(jobId);
                    scheduler.shutdownNow();
                }

                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().remove("job_" + jobId).apply();

                result.put("job_id", jobId);
                result.put("success", true);
                result.put("message", "定时任务已移除");
                break;
            }
        }
        return result;
    }

    /**
     * 停止所有定时任务（应用退出时调用）
     */
    public static void stopAll() {
        for (ScheduledExecutorService scheduler : activeJobs.values()) {
            scheduler.shutdownNow();
        }
        activeJobs.clear();
    }
}