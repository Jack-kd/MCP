package com.mcp_run;

import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.mcp_run.tools.MCPTool;
import com.mcp_run.tools.ToolRegistry;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 工具列表页面 - 展示所有MCP工具及其功能说明
 */
public class ToolListActivity extends AppCompatActivity {

    private ToolRegistry toolRegistry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tool_list);

        toolRegistry = new ToolRegistry(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("MCP 工具列表");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        LinearLayout container = findViewById(R.id.tool_list_container);
        populateToolList(container);
    }

    private void populateToolList(LinearLayout container) {
        for (String category : toolRegistry.getAllCategories()) {
            // 分类标题
            TextView catTitle = new TextView(this);
            catTitle.setText(ToolRegistry.getCategoryIcon(category) + " " + category);
            catTitle.setTextSize(18);
            catTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            catTitle.setTextColor(0xFF00BCD4);
            catTitle.setPadding(0, 24, 0, 12);
            container.addView(catTitle);

            // 该类下的工具
            for (MCPTool tool : toolRegistry.getToolsByCategory(category)) {
                View card = createToolCard(tool, category);
                container.addView(card);
            }
        }

        // 底部统计
        TextView stats = new TextView(this);
        stats.setText("共 " + toolRegistry.getToolCount() + " 个工具 · " + toolRegistry.getAllCategories().size() + " 个分类");
        stats.setTextSize(13);
        stats.setTextColor(0xFF888888);
        stats.setGravity(android.view.Gravity.CENTER);
        stats.setPadding(0, 24, 0, 24);
        container.addView(stats);
    }

    private View createToolCard(MCPTool tool, String category) {
        Context ctx = this;
        String icon = ToolRegistry.getToolIcon(tool.getName());

        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(ctx);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 8);
        card.setLayoutParams(params);
        card.setRadius(12);
        card.setCardElevation(2);
        card.setUseCompatPadding(true);
        card.setClickable(true);
        card.setFocusable(true);
        card.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E1E2E));

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(16, 12, 16, 12);

        // 工具名
        TextView nameText = new TextView(ctx);
        nameText.setText(icon + "  " + tool.getName());
        nameText.setTextSize(16);
        nameText.setTextColor(0xFF00D2FF);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(nameText);

        // 功能描述
        TextView descText = new TextView(ctx);
        descText.setText(tool.getDescription());
        descText.setTextSize(13);
        descText.setTextColor(0xFFAAAAAA);
        descText.setPadding(0, 4, 0, 0);
        descText.setLineSpacing(4, 1);
        content.addView(descText);

        // 分类标签
        TextView catLabel = new TextView(ctx);
        catLabel.setText(ToolRegistry.getCategoryIcon(category) + " " + category);
        catLabel.setTextSize(11);
        catLabel.setTextColor(0xFFE94560);
        catLabel.setPadding(0, 6, 0, 0);
        content.addView(catLabel);

        card.addView(content);

        // 点击弹出详情
        card.setOnClickListener(v -> showToolDetail(tool));

        return card;
    }

    private void showToolDetail(MCPTool tool) {
        String category = toolRegistry.getCategory(tool.getName());
        String icon = ToolRegistry.getToolIcon(tool.getName());

        StringBuilder msg = new StringBuilder();
        msg.append("📂 分类: ").append(category).append("\n\n");
        msg.append("📝 功能描述:\n").append(tool.getDescription()).append("\n\n");
        msg.append("📋 参数说明:\n");

        try {
            JSONObject schema = tool.getInputSchema();
            JSONObject props = schema.optJSONObject("properties");
            if (props != null) {
                JSONArray names = props.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String key = names.getString(i);
                        JSONObject prop = props.getJSONObject(key);
                        String desc = prop.optString("description", "");
                        String type = prop.optString("type", "");
                        String def = prop.has("default") ? " (默认: " + prop.get("default") + ")" : "";
                        boolean required = false;
                        JSONArray requiredArr = schema.optJSONArray("required");
                        if (requiredArr != null) {
                            for (int j = 0; j < requiredArr.length(); j++) {
                                if (requiredArr.getString(j).equals(key)) {
                                    required = true;
                                    break;
                                }
                            }
                        }
                        msg.append("  • ").append(key).append(required ? " ⚠️必填" : "")
                                .append(" (").append(type).append(")").append(def).append("\n");
                        msg.append("    ").append(desc).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            msg.append("  无参数\n");
        }

        new AlertDialog.Builder(this)
                .setTitle(icon + " " + tool.getName())
                .setMessage(msg.toString())
                .setPositiveButton("知道了", null)
                .show();
    }
}