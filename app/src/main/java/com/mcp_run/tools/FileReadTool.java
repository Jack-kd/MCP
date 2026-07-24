package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * 文件读取工具
 */
public class FileReadTool implements MCPTool {
    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "读取设备上的文件内容，支持文本文件。返回文件内容和元数据。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject pathProp = new JSONObject();
            pathProp.put("type", "string");
            pathProp.put("description", "文件完整路径，如 /storage/emulated/0/Download/test.txt");
            props.put("path", pathProp);
            
            JSONObject encodingProp = new JSONObject();
            encodingProp.put("type", "string");
            encodingProp.put("description", "文件编码，默认UTF-8");
            encodingProp.put("default", "UTF-8");
            props.put("encoding", encodingProp);
            
            JSONObject maxLinesProp = new JSONObject();
            maxLinesProp.put("type", "number");
            maxLinesProp.put("description", "最大读取行数，-1表示全部读取");
            maxLinesProp.put("default", -1);
            props.put("max_lines", maxLinesProp);
            
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("path"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String path = args.getString("path");
        String encoding = args.optString("encoding", "UTF-8");
        int maxLines = args.optInt("max_lines", -1);
        
        File file = new File(path);
        if (!file.exists()) {
            throw new Exception("文件不存在: " + path);
        }
        if (!file.isFile()) {
            throw new Exception("路径不是文件: " + path);
        }
        
        StringBuilder content = new StringBuilder();
        int lineCount = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), encoding))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (maxLines > 0 && lineCount >= maxLines) {
                    content.append("... (已截断，仅显示前 ").append(maxLines).append(" 行)\n");
                    break;
                }
                content.append(line).append("\n");
                lineCount++;
            }
        }
        
        JSONObject result = new JSONObject();
        result.put("path", file.getAbsolutePath());
        result.put("name", file.getName());
        result.put("size", file.length());
        result.put("line_count", lineCount);
        result.put("content", content.toString());
        result.put("last_modified", file.lastModified());
        return result;
    }
}