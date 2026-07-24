package com.mcp_run.tools;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * 数据库操作工具 - SQLite
 * 支持 db_query / db_execute / db_list_tables / db_schema
 */
public class DatabaseTool implements MCPTool {
    private final String action;

    public DatabaseTool(String action) {
        this.action = action;
    }

    @Override
    public String getName() {
        return "db_" + action;
    }

    @Override
    public String getDescription() {
        switch (action) {
            case "query": return "在SQLite数据库中执行SELECT查询，返回JSON数组格式的结果。";
            case "execute": return "在SQLite数据库中执行INSERT/UPDATE/DELETE/CREATE等非查询SQL语句。";
            case "list_tables": return "列出指定SQLite数据库中的所有表名。";
            case "schema": return "查看指定SQLite数据库中某张表的建表语句和字段信息。";
            default: return "SQLite数据库操作";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            JSONObject pathProp = new JSONObject();
            pathProp.put("type", "string");
            pathProp.put("description", "数据库文件路径，如 /sdcard/test.db。若文件不存在，db_execute 会自动创建。");
            props.put("db_path", pathProp);

            if ("query".equals(action) || "execute".equals(action)) {
                JSONObject sqlProp = new JSONObject();
                sqlProp.put("type", "string");
                sqlProp.put("description", action.equals("query") ? "SELECT查询语句" : "要执行的SQL语句（INSERT/UPDATE/DELETE/CREATE TABLE等）");
                props.put("sql", sqlProp);
            }
            if ("schema".equals(action)) {
                JSONObject tableProp = new JSONObject();
                tableProp.put("type", "string");
                tableProp.put("description", "要查看的表名，不填则列出所有表");
                props.put("table_name", tableProp);
            }

            schema.put("properties", props);
            JSONArray required = new JSONArray().put("db_path");
            if ("query".equals(action) || "execute".equals(action)) required.put("sql");
            schema.put("required", required);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String dbPath = args.getString("db_path");
        JSONObject result = new JSONObject();

        File dbFile = new File(dbPath);
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        switch (action) {
            case "query": {
                String sql = args.getString("sql");
                SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
                try {
                    Cursor cursor = db.rawQuery(sql, null);
                    JSONArray columns = new JSONArray();
                    for (String col : cursor.getColumnNames()) {
                        columns.put(col);
                    }
                    JSONArray rows = new JSONArray();
                    while (cursor.moveToNext()) {
                        JSONObject row = new JSONObject();
                        for (int i = 0; i < cursor.getColumnCount(); i++) {
                            String colName = cursor.getColumnName(i);
                            int type = cursor.getType(i);
                            if (cursor.isNull(i)) {
                                row.put(colName, JSONObject.NULL);
                            } else {
                                switch (type) {
                                    case Cursor.FIELD_TYPE_INTEGER:
                                        row.put(colName, cursor.getLong(i));
                                        break;
                                    case Cursor.FIELD_TYPE_FLOAT:
                                        row.put(colName, cursor.getDouble(i));
                                        break;
                                    case Cursor.FIELD_TYPE_BLOB:
                                        row.put(colName, "[BLOB]");
                                        break;
                                    default:
                                        row.put(colName, cursor.getString(i));
                                }
                            }
                        }
                        rows.put(row);
                    }
                    cursor.close();
                    result.put("columns", columns);
                    result.put("rows", rows);
                    result.put("row_count", rows.length());
                    result.put("success", true);
                } finally {
                    db.close();
                }
                break;
            }
            case "execute": {
                String sql = args.getString("sql");
                SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath, null,
                        SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY);
                try {
                    db.execSQL(sql);
                    result.put("success", true);
                    result.put("message", "SQL执行成功");
                } catch (Exception e) {
                    result.put("success", false);
                    result.put("error", e.getMessage());
                } finally {
                    db.close();
                }
                break;
            }
            case "list_tables": {
                SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
                try {
                    Cursor cursor = db.rawQuery(
                            "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", null);
                    JSONArray tables = new JSONArray();
                    while (cursor.moveToNext()) {
                        tables.put(cursor.getString(0));
                    }
                    cursor.close();
                    result.put("tables", tables);
                    result.put("count", tables.length());
                    result.put("success", true);
                } finally {
                    db.close();
                }
                break;
            }
            case "schema": {
                String tableName = args.optString("table_name", null);
                SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
                try {
                    if (tableName != null && !tableName.isEmpty()) {
                        Cursor cursor = db.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", new String[]{tableName});
                        JSONArray schemas = new JSONArray();
                        while (cursor.moveToNext()) {
                            schemas.put(cursor.getString(0));
                        }
                        cursor.close();
                        result.put("create_sql", schemas.length() > 0 ? schemas.getString(0) : "表不存在");
                        // 字段信息
                        Cursor pc = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
                        JSONArray columns = new JSONArray();
                        while (pc.moveToNext()) {
                            JSONObject col = new JSONObject();
                            col.put("cid", pc.getInt(0));
                            col.put("name", pc.getString(1));
                            col.put("type", pc.getString(2));
                            col.put("notnull", pc.getInt(3) == 1);
                            col.put("default", pc.isNull(4) ? JSONObject.NULL : pc.getString(4));
                            col.put("pk", pc.getInt(5) == 1);
                            columns.put(col);
                        }
                        pc.close();
                        result.put("columns", columns);
                    } else {
                        Cursor cursor = db.rawQuery("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name", null);
                        JSONArray tables = new JSONArray();
                        while (cursor.moveToNext()) {
                            JSONObject t = new JSONObject();
                            t.put("name", cursor.getString(0));
                            t.put("sql", cursor.getString(1));
                            tables.put(t);
                        }
                        cursor.close();
                        result.put("tables", tables);
                    }
                    result.put("success", true);
                } finally {
                    db.close();
                }
                break;
            }
        }
        return result;
    }
}