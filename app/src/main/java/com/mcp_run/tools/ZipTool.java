package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 压缩/解压工具
 * 支持 zip_create / zip_extract / zip_list
 */
public class ZipTool implements MCPTool {
    private final String action;

    public ZipTool(String action) {
        this.action = action;
    }

    @Override
    public String getName() {
        return "zip_" + action;
    }

    @Override
    public String getDescription() {
        switch (action) {
            case "create": return "将指定文件或文件夹压缩为ZIP文件。";
            case "extract": return "解压ZIP文件到指定目录。";
            case "list": return "列出ZIP文件中的内容，不实际解压。";
            default: return "ZIP压缩/解压操作";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            if ("create".equals(action)) {
                JSONObject sourceProp = new JSONObject();
                sourceProp.put("type", "string");
                sourceProp.put("description", "要压缩的源文件或文件夹路径");
                props.put("source", sourceProp);

                JSONObject destProp = new JSONObject();
                destProp.put("type", "string");
                destProp.put("description", "目标ZIP文件路径，如 /sdcard/backup.zip");
                props.put("destination", destProp);
            } else if ("extract".equals(action)) {
                JSONObject zipProp = new JSONObject();
                zipProp.put("type", "string");
                zipProp.put("description", "ZIP文件路径");
                props.put("zip_path", zipProp);

                JSONObject destProp = new JSONObject();
                destProp.put("type", "string");
                destProp.put("description", "解压目标目录");
                props.put("extract_to", destProp);
            } else if ("list".equals(action)) {
                JSONObject zipProp = new JSONObject();
                zipProp.put("type", "string");
                zipProp.put("description", "ZIP文件路径");
                props.put("zip_path", zipProp);
            }

            schema.put("properties", props);
            JSONArray required = new JSONArray();
            if ("create".equals(action)) {
                required.put("source").put("destination");
            } else {
                required.put("zip_path");
            }
            if ("extract".equals(action)) required.put("extract_to");
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
            case "create": {
                String source = args.getString("source");
                String destination = args.getString("destination");
                File srcFile = new File(source);
                if (!srcFile.exists()) {
                    result.put("success", false);
                    result.put("error", "源文件/目录不存在: " + source);
                    break;
                }
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destination))) {
                    int count = addToZip(srcFile, srcFile.isDirectory() ? srcFile.getName() : null, zos, srcFile.getParent());
                    result.put("success", true);
                    result.put("destination", destination);
                    result.put("entry_count", count);
                    result.put("source_size", srcFile.length());
                    result.put("zip_size", new File(destination).length());
                }
                break;
            }
            case "extract": {
                String zipPath = args.getString("zip_path");
                String extractTo = args.getString("extract_to");
                File destDir = new File(extractTo);
                if (!destDir.exists()) destDir.mkdirs();

                int count = 0;
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];
                    while ((entry = zis.getNextEntry()) != null) {
                        File outFile = new File(extractTo, entry.getName());
                        if (entry.isDirectory()) {
                            outFile.mkdirs();
                        } else {
                            outFile.getParentFile().mkdirs();
                            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                        }
                        count++;
                        zis.closeEntry();
                    }
                }
                result.put("success", true);
                result.put("extracted_to", extractTo);
                result.put("entry_count", count);
                break;
            }
            case "list": {
                String zipPath = args.getString("zip_path");
                File zipFile = new File(zipPath);
                if (!zipFile.exists()) {
                    result.put("success", false);
                    result.put("error", "ZIP文件不存在: " + zipPath);
                    break;
                }
                JSONArray entries = new JSONArray();
                long totalSize = 0;
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        JSONObject e = new JSONObject();
                        e.put("name", entry.getName());
                        e.put("size", entry.getSize());
                        e.put("compressed_size", entry.getCompressedSize());
                        e.put("is_directory", entry.isDirectory());
                        e.put("last_modified", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                .format(new java.util.Date(entry.getTime())));
                        entries.put(e);
                        totalSize += entry.getSize();
                        zis.closeEntry();
                    }
                }
                result.put("success", true);
                result.put("entries", entries);
                result.put("entry_count", entries.length());
                result.put("total_size", totalSize);
                result.put("zip_size", zipFile.length());
                break;
            }
        }
        return result;
    }

    private int addToZip(File file, String parentPath, ZipOutputStream zos, String rootPath) throws IOException {
        int count = 0;
        if (file.isDirectory()) {
            String dirPath = parentPath != null ? parentPath + "/" : "";
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    count += addToZip(child, dirPath + child.getName(), zos, rootPath);
                }
            }
        } else {
            String entryName = file.getAbsolutePath().substring(rootPath != null ? rootPath.length() + 1 : 0);
            if (parentPath != null) entryName = parentPath;
            zos.putNextEntry(new ZipEntry(entryName));
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
            count = 1;
        }
        return count;
    }
}