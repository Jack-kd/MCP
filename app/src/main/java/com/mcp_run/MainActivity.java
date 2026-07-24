package com.mcp_run;

import android.Manifest;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.mcp_run.tools.ScreenshotTool;
import com.mcp_run.tools.ToolRegistry;

public class MainActivity extends AppCompatActivity 
        implements NavigationView.OnNavigationItemSelectedListener {
    
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_CODE_SAF = 101;
    private static final String PREFS_NAME = "mcp_config";
    private static final String KEY_PORT = "server_port";
    private static final String KEY_WORKSPACE = "workspace_path";
    
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private MaterialToolbar toolbar;
    private TextView statusText, ipText, logText;
    private EditText portEdit, workspaceEdit;
    private Button toggleButton;
    private View statusIndicator;
    private ScrollView logScrollView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;
    private int currentPort = 1145;
    private ToolRegistry toolRegistry;
    
    private static String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 30) {
            return new String[]{
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            };
        } else {
            return new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            };
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 深色模式：在 super.onCreate 之前应用
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int nightMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(nightMode);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolRegistry = new ToolRegistry(this);
        initViews();
        loadPreferences();
        requestPermissionsOnFirstLaunch();
        updateServerStatus(MCPService.isRunning());
        setupDrawer();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateServerStatus(MCPService.isRunning());
        registerClipboardListener();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        unregisterClipboardListener();
    }
    
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        drawerLayout.closeDrawer(GravityCompat.START);
        int id = item.getItemId();
        if (id == R.id.nav_server_status) return true;
        if (id == R.id.nav_tool_list) {
            startActivity(new Intent(this, ToolListActivity.class));
            return true;
        }
        if (id == R.id.nav_dark_mode) { toggleDarkMode(); return true; }
        if (id == R.id.nav_about) { showAboutDialog(); return true; }
        if (id == R.id.nav_exit) { finishAffinity(); return true; }
        return true;
    }
    
    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
        statusText = findViewById(R.id.status_text);
        ipText = findViewById(R.id.ip_text);
        logText = findViewById(R.id.log_text);
        portEdit = findViewById(R.id.port_edit);
        workspaceEdit = findViewById(R.id.workspace_edit);
        toggleButton = findViewById(R.id.toggle_server_button);
        statusIndicator = findViewById(R.id.status_indicator);
        logScrollView = findViewById(R.id.log_scroll_view);
        toggleButton.setOnClickListener(v -> {
            if (isRunning) {
                stopServer();
            } else {
                startServer();
            }
        });
        findViewById(R.id.select_folder_button).setOnClickListener(v -> openDocumentTree());
        findViewById(R.id.copy_ip_button).setOnClickListener(v -> copyAddress());
        findViewById(R.id.clear_log_button).setOnClickListener(v -> logText.setText(""));
        ipText.setText("127.0.0.1 (仅本机)");
    }
    
    private void setupDrawer() {
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        navigationView.setNavigationItemSelectedListener(this);
        View headerView = navigationView.getHeaderView(0);
        TextView versionText = headerView.findViewById(R.id.nav_header_version);
        if (versionText != null) {
            versionText.setText("v1.1 · " + toolRegistry.getToolCount() + " 个工具");
        }
    }
    
    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentPort = prefs.getInt(KEY_PORT, 1145);
        portEdit.setText(String.valueOf(currentPort));
        String savedWorkspace = prefs.getString(KEY_WORKSPACE, "/storage/emulated/0/");
        workspaceEdit.setText(savedWorkspace);
    }
    
    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(KEY_PORT, currentPort).putString(KEY_WORKSPACE, workspaceEdit.getText().toString().trim()).apply();
    }
    
    private void requestPermissionsOnFirstLaunch() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean firstLaunch = prefs.getBoolean("first_launch", true);
        if (!firstLaunch) return;
        prefs.edit().putBoolean("first_launch", false).apply();
        addLog("首次启动，正在请求系统权限...");
        for (String perm : getRequiredPermissions()) {
            if (Build.VERSION.SDK_INT >= 30 && Manifest.permission.MANAGE_EXTERNAL_STORAGE.equals(perm)) {
                if (!Environment.isExternalStorageManager()) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    addLog("🔓 请授予「所有文件访问权限」");
                }
            } else if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{perm}, PERMISSION_REQUEST_CODE);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                String perm = permissions[i];
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                addLog((granted ? "✅" : "❌") + " 权限: " + perm + (granted ? " 已授予" : " 被拒绝"));
            }
        }
    }
    
    private void startServer() {
        String portStr = portEdit.getText().toString().trim();
        if (portStr.isEmpty()) portStr = "1145";
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                Toast.makeText(this, "端口范围: 1024-65535", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "端口格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        currentPort = port;
        savePreferences();
        portEdit.setEnabled(false);
        workspaceEdit.setEnabled(false);
        ScreenshotTool.setRootView(getWindow().getDecorView());
        Intent intent = new Intent(this, MCPService.class);
        intent.putExtra(MCPService.EXTRA_PORT, port);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        updateServerStatus(true);
        addLog("🚀 正在启动MCP服务器 (端口: " + port + ")...");
        handler.postDelayed(() -> {
            boolean running = MCPService.isRunning();
            updateServerStatus(running);
            if (running) {
                String ws = workspaceEdit.getText().toString().trim();
                addLog("✅ MCP服务器已启动");
                addLog("📡 端口: " + port);
                addLog("📂 工作区: " + ws);
                addLog("🌐 端点: http://127.0.0.1:" + port + "/mcp");
                addLog("🔧 工具数: " + toolRegistry.getToolCount());
                addLog("📡 协议: Streamable HTTP (MCP 2025-03-26)");
                Toast.makeText(this, "✅ MCP服务器已启动", Toast.LENGTH_SHORT).show();
            } else {
                addLog("❌ 服务器启动失败，端口 " + port + " 可能被占用");
                portEdit.setEnabled(true);
                workspaceEdit.setEnabled(true);
            }
        }, 2000);
    }
    
    private void stopServer() {
        Intent intent = new Intent(this, MCPService.class);
        intent.setAction(MCPService.ACTION_STOP);
        startService(intent);
        updateServerStatus(false);
        portEdit.setEnabled(true);
        workspaceEdit.setEnabled(true);
        addLog("🛑 MCP服务器已停止");
        Toast.makeText(this, "MCP服务器已停止", Toast.LENGTH_SHORT).show();
    }
    
    private void updateServerStatus(boolean running) {
        isRunning = running;
        runOnUiThread(() -> {
            if (running) {
                statusText.setText("● 运行中");
                statusText.setTextColor(getColor(android.R.color.holo_green_dark));
                statusIndicator.setBackgroundColor(getColor(android.R.color.holo_green_dark));
                toggleButton.setText("⏹ 停止服务器");
                toggleButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF44336));
                portEdit.setEnabled(false); workspaceEdit.setEnabled(false);
            } else {
                statusText.setText("○ 已停止");
                statusText.setTextColor(getColor(android.R.color.holo_red_dark));
                statusIndicator.setBackgroundColor(getColor(android.R.color.darker_gray));
                toggleButton.setText("▶ 启动服务器");
                toggleButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2196F3));
                portEdit.setEnabled(true); workspaceEdit.setEnabled(true);
            }
        });
    }
    
    private void copyAddress() {
        String text = "http://127.0.0.1:" + currentPort + "/mcp";
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("MCP地址", text));
        Toast.makeText(this, "已复制: " + text, Toast.LENGTH_SHORT).show();
        addLog("📋 已复制端点: " + text);
    }

    private void openDocumentTree() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_SAF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SAF && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String path = safUriToPath(uri);
                workspaceEdit.setText(path);
                addLog("📂 SAF 工作区: " + path);
            }
        }
    }

    private String safUriToPath(Uri uri) {
        // content://com.android.externalstorage.documents/tree/primary%3A%E8%84%9A%E6%9C%AC
        // → /storage/emulated/0/脚本/
        String docId;
        try {
            docId = DocumentsContract.getTreeDocumentId(uri);
        } catch (Exception e) {
            return uri.toString();
        }
        // primary:脚本 → /storage/emulated/0/脚本/
        if (docId.startsWith("primary:")) {
            String relativePath = docId.substring("primary:".length());
            return "/storage/emulated/0/" + (relativePath.isEmpty() ? "" : relativePath + "/");
        }
        if (docId.startsWith("home:")) {
            String relativePath = docId.substring("home:".length());
            return "/storage/emulated/0/" + (relativePath.isEmpty() ? "" : relativePath + "/");
        }
        return uri.toString();
    }
    
    private void addLog(String message) {
        runOnUiThread(() -> {
            String ts = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            logText.append("[" + ts + "] " + message + "\n");
            handler.postDelayed(() -> logScrollView.fullScroll(View.FOCUS_DOWN), 100);
        });
    }
    
    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("关于 MCP Tool")
                .setMessage("MCP Tool v1.1\n\nAndroid MCP 协议服务器\nStreamable HTTP 传输协议\n127.0.0.1 本地回环地址\n\n"
                        + "📁 文件操作 · ⚙️ 系统管理\n📱 设备信息 · 📦 应用管理\n"
                        + "🐍 脚本执行 · 💬 通讯交互\n🌐 网络请求 · 🧰 实用工具\n\n"
                        + "共 " + toolRegistry.getToolCount() + " 个工具")
                .setPositiveButton("确定", null)
                .show();
    }

    private void toggleDarkMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int currentMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        String[] modeLabels = {"跟随系统", "始终浅色", "始终深色"};
        int[] modeValues = {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES
        };

        int currentIndex = 0;
        for (int i = 0; i < modeValues.length; i++) {
            if (modeValues[i] == currentMode) { currentIndex = i; break; }
        }

        new AlertDialog.Builder(this)
                .setTitle("深色模式")
                .setSingleChoiceItems(modeLabels, currentIndex, (dialog, which) -> {
                    prefs.edit().putInt("night_mode", modeValues[which]).apply();
                    AppCompatDelegate.setDefaultNightMode(modeValues[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;
    
    private void registerClipboardListener() {
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardListener = () -> {
            if (clipboardManager.hasPrimaryClip()) {
                CharSequence text = clipboardManager.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    addLog("📋 剪贴板已更新: " + (text.length() > 50 ? text.subSequence(0, 50) + "..." : text));
                }
            }
        };
        clipboardManager.addPrimaryClipChangedListener(clipboardListener);
    }
    
    private void unregisterClipboardListener() {
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }
    }
}