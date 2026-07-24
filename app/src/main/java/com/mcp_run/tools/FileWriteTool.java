package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * 文件写入工具
 */
public class FileWriteTool implements MCPTool {
    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "向设备上的文件写入内容，支持创建新文件和覆盖现有文件。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject pathProp = new JSONObject();
            pathProp.put("type", "string");
            pathProp.put("description", "文件完整路径");
            props.put("path", pathProp);
            
            JSONObject contentProp = new JSONObject();
            contentProp.put("type", "string");
            contentProp.put("description", "要写入的文件内容");
            props.put("content", contentProp);
            
            JSONObject appendProp = new JSONObject();
            appendProp.put("type", "boolean");
            appendProp.put("description", "是否追加到文件末尾（而不是覆盖）");
            appendProp.put("default", false);
            props.put("append", appendProp);
            
            JSONObject encodingProp = new JSONObject();
            encodingProp.put("type", "string");
            encodingProp.put("description", "文件编码，默认UTF-8");
            encodingProp.put("default", "UTF-8");
            props.put("encoding", encodingProp);
            
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("path").put("content"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String path = args.getString("path");
        String content = args.getString("content");
        boolean append = args.optBoolean("append", false);
        String encoding = args.optString("encoding", "UTF-8");
        
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        boolean existed = file.exists();
        long oldSize = existed ? file.length() : 0;
        
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file, append), encoding)) {
            writer.write(content);
            writer.flush();
        }
        
        JSONObject result = new JSONObject();
        result.put("path", file.getAbsolutePath());
        result.put("name", file.getName());
        result.put("size", file.length());
        result.put("existed_before", existed);
        result.put("old_size", oldSize);
        result.put("was_appended", append);
        result.put("bytes_written", content.getBytes(encoding).length);
        return result;
    }
}