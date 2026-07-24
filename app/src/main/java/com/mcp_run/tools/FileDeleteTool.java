package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * 文件删除工具
 */
public class FileDeleteTool implements MCPTool {
    @Override
    public String getName() {
        return "delete_file";
    }

    @Override
    public String getDescription() {
        return "删除文件或空目录。支持强制递归删除目录。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject pathProp = new JSONObject();
            pathProp.put("type", "string");
            pathProp.put("description", "要删除的文件或目录路径");
            props.put("path", pathProp);
            
            JSONObject recursiveProp = new JSONObject();
            recursiveProp.put("type", "boolean");
            recursiveProp.put("description", "是否递归删除（目录非空时需要）");
            recursiveProp.put("default", false);
            props.put("recursive", recursiveProp);
            
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
        boolean recursive = args.optBoolean("recursive", false);
        
        File file = new File(path);
        if (!file.exists()) {
            throw new Exception("路径不存在: " + path);
        }
        
        long size = file.length();
        boolean isDir = file.isDirectory();
        boolean deleted;
        
        if (isDir && recursive) {
            deleted = deleteRecursive(file);
        } else {
            deleted = file.delete();
        }
        
        if (!deleted) {
            if (isDir && !recursive) {
                throw new Exception("目录非空，请使用 recursive=true 递归删除");
            }
            throw new Exception("删除失败: " + path);
        }
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("path", file.getAbsolutePath());
        result.put("was_directory", isDir);
        result.put("freed_bytes", size);
        return result;
    }
    
    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }
}