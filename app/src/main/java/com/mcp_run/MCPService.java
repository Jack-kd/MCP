package com.mcp_run;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;

/**
 * MCP前台服务 - 保持MCP HTTP服务器在后台运行
 * 支持通过Intent Extra传递自定义端口
 */
public class MCPService extends Service {
    private static final String TAG = "MCPService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "mcp_server_channel";
    public static final String EXTRA_PORT = "extra_port";
    public static final String ACTION_STOP = "STOP";
    
    private MCPHttpServer server;
    private static MCPService instance;
    private static int currentPort = 8910;
    
    public static boolean isRunning() {
        return instance != null;
    }
    
    public static MCPService getInstance() {
        return instance;
    }
    
    public static int getCurrentPort() {
        return currentPort;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // 读取自定义端口
        int port = intent != null ? intent.getIntExtra(EXTRA_PORT, 8910) : 8910;
        currentPort = port;
        
        // 启动前台通知
        Notification notification = createNotification("MCP服务器启动中...", false);
        startForeground(NOTIFICATION_ID, notification);
        
        // 启动MCP服务器
        startMCPServer(port);
        
        return START_STICKY;
    }
    
    private void startMCPServer(int port) {
        new Thread(() -> {
            try {
                server = new MCPHttpServer(this, port);
                server.setStatusListener(new MCPHttpServer.ServerStatusListener() {
                    @Override
                    public void onStatusChanged(String status, int port) {
                        updateNotification(status, server.isRunning());
                        Log.d(TAG, status);
                    }
                    
                    @Override
                    public void onRequest(String method, String path, int responseCode) {
                        Log.d(TAG, method + " " + path + " -> " + responseCode);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, error);
                    }
                });
                server.start();
                
                // 更新通知
                updateNotification("MCP服务器运行中 端口:" + server.getPort(), true);
                
            } catch (IOException e) {
                Log.e(TAG, "启动服务器失败", e);
                updateNotification("启动失败: " + e.getMessage(), false);
            }
        }, "MCP-Server-Start").start();
    }
    
    private void updateNotification(String text, boolean isRunning) {
        Notification notification = createNotification(text, isRunning);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification);
    }
    
    private Notification createNotification(String text, boolean isRunning) {
        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        Intent stopIntent = new Intent(this, MCPService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        String title = isRunning ? "🟢 MCP服务器运行中" : "🔴 MCP服务器已停止";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isRunning)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止服务", stopPendingIntent);
        
        return builder.build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MCP服务器",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("MCP服务器的运行状态通知");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }
    
    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
        }
        instance = null;
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}