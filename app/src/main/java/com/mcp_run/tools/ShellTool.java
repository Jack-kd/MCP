package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Shell命令执行工具
 */
public class ShellTool implements MCPTool {
    @Override
    public String getName() {
        return "shell";
    }

    @Override
    public String getDescription() {
        return "在设备上执行Shell命令（通过sh），返回命令输出。支持设置超时和工作目录。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject cmdProp = new JSONObject();
            cmdProp.put("type", "string");
            cmdProp.put("description", "要执行的Shell命令");
            props.put("command", cmdProp);
            
            JSONObject timeoutProp = new JSONObject();
            timeoutProp.put("type", "number");
            timeoutProp.put("description", "超时时间（毫秒），默认30000（30秒）");
            timeoutProp.put("default", 30000);
            props.put("timeout_ms", timeoutProp);
            
            JSONObject workDirProp = new JSONObject();
            workDirProp.put("type", "string");
            workDirProp.put("description", "工作目录，默认不设置");
            props.put("working_directory", workDirProp);
            
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("command"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String command = args.getString("command");
        long timeoutMs = args.optLong("timeout_ms", 30000);
        String workingDir = args.optString("working_directory", null);
        
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        if (workingDir != null && !workingDir.isEmpty()) {
            pb.directory(new java.io.File(workingDir));
        }
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // 读取输出
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();
        
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (Exception e) {
                errorOutput.append(e.getMessage());
            }
        });
        outputReader.start();
        
        // 等待完成或超时
        boolean finished = true;
        long startTime = System.currentTimeMillis();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            process.destroy();
            finished = false;
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        outputReader.join(2000);
        
        int exitCode = process.exitValue();
        
        JSONObject result = new JSONObject();
        result.put("command", command);
        result.put("exit_code", exitCode);
        result.put("stdout", output.toString().trim());
        result.put("stderr", errorOutput.toString().trim());
        result.put("elapsed_ms", elapsed);
        result.put("success", exitCode == 0);
        return result;
    }
}