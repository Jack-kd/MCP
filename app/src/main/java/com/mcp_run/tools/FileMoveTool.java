package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

/**
 * 文件移动/复制工具
 */
public class FileMoveTool implements MCPTool {
    @Override
    public String getName() {
        return "move_file";
    }

    @Override
    public String getDescription() {
        return "移动或复制文件/目录到目标路径。支持在同文件系统内移动，跨文件系统时自动复制+删除。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject sourceProp = new JSONObject();
            sourceProp.put("type", "string");
            sourceProp.put("description", "源路径");
            props.put("source", sourceProp);
            
            JSONObject destProp = new JSONObject();
            destProp.put("type", "string");
            destProp.put("description", "目标路径");
            props.put("destination", destProp);
            
            JSONObject actionProp = new JSONObject();
            actionProp.put("type", "string");
            actionProp.put("description", "操作类型: move（移动）或 copy（复制）");
            actionProp.put("default", "move");
            actionProp.put("enum", new JSONArray().put("move").put("copy"));
            props.put("action", actionProp);
            
            JSONObject overwriteProp = new JSONObject();
            overwriteProp.put("type", "boolean");
            overwriteProp.put("description", "是否覆盖已存在的目标文件");
            overwriteProp.put("default", false);
            props.put("overwrite", overwriteProp);
            
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("source").put("destination"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String source = args.getString("source");
        String destination = args.getString("destination");
        String action = args.optString("action", "move");
        boolean overwrite = args.optBoolean("overwrite", false);
        
        File srcFile = new File(source);
        File destFile = new File(destination);
        
        if (!srcFile.exists()) {
            throw new Exception("源路径不存在: " + source);
        }
        
        // 检查目标
        if (destFile.exists() && !overwrite) {
            throw new Exception("目标已存在且未设置覆盖: " + destination);
        }
        
        // 确保目标父目录存在
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        long startTime = System.currentTimeMillis();
        
        if ("move".equals(action)) {
            boolean moved = srcFile.renameTo(destFile);
            if (!moved) {
                // 跨文件系统，使用复制+删除
                copyFileOrDir(srcFile, destFile);
                deleteRecursive(srcFile);
                moved = true;
            }
            if (!moved) {
                throw new Exception("移动失败");
            }
        } else {
            copyFileOrDir(srcFile, destFile);
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("action", action);
        result.put("source", srcFile.getAbsolutePath());
        result.put("destination", destFile.getAbsolutePath());
        result.put("source_size", srcFile.length());
        result.put("destination_size", destFile.length());
        result.put("is_directory", srcFile.isDirectory());
        result.put("elapsed_ms", elapsed);
        return result;
    }
    
    private void copyFileOrDir(File src, File dest) throws Exception {
        if (src.isDirectory()) {
            dest.mkdirs();
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyFileOrDir(child, new File(dest, child.getName()));
                }
            }
        } else {
            try (FileInputStream fis = new FileInputStream(src);
                 FileOutputStream fos = new FileOutputStream(dest);
                 FileChannel inChannel = fis.getChannel();
                 FileChannel outChannel = fos.getChannel()) {
                inChannel.transferTo(0, inChannel.size(), outChannel);
            }
        }
    }
    
    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}