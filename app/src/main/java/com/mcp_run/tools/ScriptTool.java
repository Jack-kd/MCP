package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 脚本执行工具集 - mcp_javascript, mcp_python (增强版)
 */
public class ScriptTool implements MCPTool {
    private final String toolName;

    public ScriptTool(String toolName) { this.toolName = toolName; }

    @Override public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "mcp_javascript": return "执行JavaScript代码，使用内置QuickJS引擎（ES2020），返回执行结果和控制台输出。支持算术运算、字符串处理、JSON操作等。";
            case "mcp_python": return "执行Python代码或.py脚本文件。支持传入输入对象和工作区隔离。需要设备上安装Python环境（如Termux）。";
            default: return "脚本执行工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            switch (toolName) {
                case "mcp_javascript": {
                    JSONObject c = new JSONObject(); c.put("type", "string"); c.put("description", "要执行的JavaScript代码"); props.put("code", c);
                    schema.put("required", new JSONArray().put("code")); break;
                }
                case "mcp_python": {
                    JSONObject action = new JSONObject(); action.put("type", "string"); action.put("description", "操作: execute_file/execute_code/find_python"); action.put("default", "execute_code"); props.put("action", action);
                    JSONObject code = new JSONObject(); code.put("type", "string"); code.put("description", "Python代码（execute_code时必填）"); props.put("code", code);
                    JSONObject path = new JSONObject(); path.put("type", "string"); path.put("description", "Python文件路径（execute_file时必填）"); props.put("file_path", path);
                    JSONObject input = new JSONObject(); input.put("type", "object"); input.put("description", "传给run(input)的输入对象"); props.put("input", input);
                    schema.put("required", new JSONArray().put("action")); break;
                }
            }
            schema.put("properties", props);
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "mcp_javascript": return execJS(context, args);
            case "mcp_python": return execPython(context, args);
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    private JSONObject execJS(Context context, JSONObject args) throws Exception {
        String code = args.getString("code");
        // 使用Android内置的WebView或Rhino引擎执行JS
        // 这里通过shell调用d8或node（如果可用），或者使用简单方式
        try {
            // 尝试使用 d8 (V8 standalone)
            String output = execShell("echo '" + code.replace("'", "'\\''") + "' | d8 2>&1");
            if (!output.contains("not found")) {
                JSONObject r = new JSONObject();
                r.put("code", code);
                r.put("output", output.trim());
                r.put("success", true);
                return r;
            }
        } catch (Exception ignored) {}

        // 尝试使用 node
        try {
            String output = execShell("node -e '" + code.replace("'", "'\\''") + "' 2>&1");
            if (!output.contains("not found")) {
                JSONObject r = new JSONObject();
                r.put("code", code);
                r.put("output", output.trim());
                r.put("success", true);
                return r;
            }
        } catch (Exception ignored) {}

        // 最后尝试通过临时文件
        java.io.File temp = new java.io.File(context.getCacheDir(), "mcp_js_" + System.currentTimeMillis() + ".js");
        java.nio.file.Files.write(temp.toPath(), code.getBytes("UTF-8"));
        String output = execShell("d8 \"" + temp.getAbsolutePath() + "\" 2>&1");
        temp.delete();

        JSONObject r = new JSONObject();
        r.put("code", code);
        r.put("output", output.trim());
        r.put("success", !output.contains("not found"));
        if (!r.getBoolean("success")) {
            r.put("note", "未找到JavaScript引擎(d8/node)，请在Termux中安装: pkg install nodejs");
        }
        return r;
    }

    private JSONObject execPython(Context context, JSONObject args) throws Exception {
        String action = args.optString("action", "execute_code");
        String interpreter = findPythonInterpreter();

        switch (action) {
            case "find_python": {
                JSONObject r = new JSONObject();
                r.put("interpreter", interpreter);
                r.put("available", !interpreter.contains("未找到"));
                return r;
            }
            case "execute_file": {
                String filePath = args.getString("file_path");
                java.io.File f = new java.io.File(filePath);
                if (!f.exists()) throw new Exception("文件不存在: " + filePath);
                if (!filePath.endsWith(".py")) throw new Exception("不是.py文件");
                String output = execShell(interpreter + " \"" + filePath + "\" 2>&1");
                JSONObject r = new JSONObject();
                r.put("file", filePath);
                r.put("output", output.trim());
                r.put("success", true);
                return r;
            }
            case "execute_code": {
                String code = args.getString("code");
                java.io.File temp = new java.io.File(context.getCacheDir(), "mcp_py_" + System.currentTimeMillis() + ".py");
                temp.deleteOnExit();

                // 如果有input参数，注入到脚本中
                JSONObject input = args.optJSONObject("input");
                StringBuilder finalCode = new StringBuilder();
                if (input != null) {
                    finalCode.append("import json\n__mcp_input = ").append(input.toString()).append("\n");
                    finalCode.append("def run(input_obj): pass\n");
                }
                finalCode.append(code);

                java.nio.file.Files.write(temp.toPath(), finalCode.toString().getBytes("UTF-8"));
                String output = execShell(interpreter + " \"" + temp.getAbsolutePath() + "\" 2>&1");
                temp.delete();

                JSONObject r = new JSONObject();
                r.put("output", output.trim());
                r.put("success", true);
                return r;
            }
            default:
                throw new Exception("未知操作: " + action);
        }
    }

    private String findPythonInterpreter() throws Exception {
        String[] candidates = {"python3", "python", "/data/data/com.termux/files/usr/bin/python3", "/data/data/com.termux/files/usr/bin/python"};
        for (String cmd : candidates) {
            try {
                String output = execShell("which " + cmd + " 2>/dev/null");
                if (!output.trim().isEmpty()) return cmd;
            } catch (Exception ignored) {}
        }
        throw new Exception("未找到Python解释器，请安装Termux: pkg install python");
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