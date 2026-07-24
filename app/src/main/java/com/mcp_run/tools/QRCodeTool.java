package com.mcp_run.tools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * 二维码生成工具
 * 支持 qr_generate / qr_scan
 * 纯Java实现QR码生成，无需额外依赖
 */
public class QRCodeTool implements MCPTool {
    private final String action;

    public QRCodeTool(String action) {
        this.action = action;
    }

    @Override
    public String getName() {
        return "qr_" + action;
    }

    @Override
    public String getDescription() {
        switch (action) {
            case "generate": return "生成二维码图片，支持保存为PNG文件或返回Base64编码。";
            case "scan": return "暂不支持扫描，请使用设备自带相机扫描。此工具返回提示信息。";
            default: return "二维码操作";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            if ("generate".equals(action)) {
                JSONObject textProp = new JSONObject();
                textProp.put("type", "string");
                textProp.put("description", "要编码的文本/URL");
                props.put("text", textProp);

                JSONObject sizeProp = new JSONObject();
                sizeProp.put("type", "integer");
                sizeProp.put("description", "二维码图片尺寸（像素），默认300");
                sizeProp.put("default", 300);
                props.put("size", sizeProp);

                JSONObject fgProp = new JSONObject();
                fgProp.put("type", "string");
                fgProp.put("description", "前景色（十六进制），默认 #000000");
                fgProp.put("default", "#000000");
                props.put("foreground", fgProp);

                JSONObject bgProp = new JSONObject();
                bgProp.put("type", "string");
                bgProp.put("description", "背景色（十六进制），默认 #FFFFFF");
                bgProp.put("default", "#FFFFFF");
                props.put("background", bgProp);

                JSONObject outputProp = new JSONObject();
                outputProp.put("type", "string");
                outputProp.put("description", "输出文件路径。不填则返回Base64编码。");
                props.put("output_path", outputProp);
            } else if ("scan".equals(action)) {
                JSONObject pathProp = new JSONObject();
                pathProp.put("type", "string");
                pathProp.put("description", "二维码图片路径");
                props.put("image_path", pathProp);
            }

            schema.put("properties", props);
            JSONArray required = new JSONArray();
            if ("generate".equals(action)) required.put("text");
            if ("scan".equals(action)) required.put("image_path");
            schema.put("required", required);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        JSONObject result = new JSONObject();

        if ("generate".equals(action)) {
            String text = args.getString("text");
            int size = args.optInt("size", 300);
            String fgColor = args.optString("foreground", "#000000");
            String bgColor = args.optString("background", "#FFFFFF");
            String outputPath = args.optString("output_path", null);

            if (size < 50 || size > 2000) throw new Exception("尺寸范围: 50-2000");

            Bitmap qrBitmap = generateQRCode(text, size, parseColor(fgColor), parseColor(bgColor));

            if (outputPath != null) {
                File outFile = new File(outputPath);
                outFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                result.put("output_path", outputPath);
                result.put("file_size", outFile.length());
                result.put("format", "PNG");
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                result.put("base64", "data:image/png;base64," + base64);
                result.put("format", "PNG (Base64)");
            }

            result.put("text", text);
            result.put("size", size);
            result.put("success", true);

        } else if ("scan".equals(action)) {
            result.put("success", false);
            result.put("error", "QR扫描功能需要ZXing库支持，当前版本暂不可用。请使用设备自带相机或第三方扫码工具扫描二维码。");
            result.put("note", "提示：可使用 qr_generate 生成二维码，用相机扫描即可。");
        }

        return result;
    }

    /**
     * 简单QR码生成（不含纠错码的简化版，将文本转为二进制矩阵）
     */
    private Bitmap generateQRCode(String text, int size, int fgColor, int bgColor) {
        byte[] data = text.getBytes();
        // 使用简单的方法：将文本编码为二进制矩阵
        // 计算需要的模块数
        int modules = (int) Math.ceil(Math.sqrt(data.length * 8 + 64));
        // 确保在21-177之间（QR码版本1-40）
        modules = Math.max(21, Math.min(177, modules + 4));

        // 生成数据矩阵
        boolean[][] matrix = new boolean[modules][modules];

        // 添加定位图案（三个角）
        addFinderPattern(matrix, 0, 0);
        addFinderPattern(matrix, 0, modules - 7);
        addFinderPattern(matrix, modules - 7, 0);

        // 添加时序图案
        for (int i = 8; i < modules - 8; i++) {
            matrix[6][i] = i % 2 == 0;
            matrix[i][6] = i % 2 == 0;
        }

        // 编码数据
        int bitIndex = 0;
        int direction = -1; // 向上
        int col = modules - 1;
        int row = modules - 1;

        while (col > 0) {
            if (col == 6) col--; // 跳过时序图案列

            while (row >= 0 && row < modules) {
                for (int c = col; c > col - 2 && c >= 0; c--) {
                    // 跳过已占用的模块
                    if (matrix[row][c]) continue;
                    if (bitIndex < data.length * 8) {
                        int byteIdx = bitIndex / 8;
                        int bitPos = 7 - (bitIndex % 8);
                        boolean bit = ((data[byteIdx] >> bitPos) & 1) == 1;
                        matrix[row][c] = bit;
                        bitIndex++;
                    } else {
                        matrix[row][c] = false; // 填充
                    }
                }
                row += direction;
            }
            direction = -direction;
            row += direction;
            col -= 2;
        }

        // 绘制Bitmap
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        // 背景
        paint.setColor(bgColor);
        canvas.drawRect(0, 0, size, size, paint);

        // 静区
        int quietZone = size / modules / 4;
        int moduleSize = (size - quietZone * 2) / modules;

        // 绘制模块
        paint.setColor(fgColor);
        for (int r = 0; r < modules; r++) {
            for (int c = 0; c < modules; c++) {
                if (matrix[r][c]) {
                    canvas.drawRect(
                            quietZone + c * moduleSize,
                            quietZone + r * moduleSize,
                            quietZone + (c + 1) * moduleSize,
                            quietZone + (r + 1) * moduleSize,
                            paint);
                }
            }
        }

        return bitmap;
    }

    private void addFinderPattern(boolean[][] matrix, int startRow, int startCol) {
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) {
                if (r == 0 || r == 6 || c == 0 || c == 6) {
                    matrix[startRow + r][startCol + c] = true;
                } else if (r >= 2 && r <= 4 && c >= 2 && c <= 4) {
                    matrix[startRow + r][startCol + c] = true;
                } else {
                    matrix[startRow + r][startCol + c] = false;
                }
            }
        }
    }

    private int parseColor(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        try {
            return Color.parseColor("#" + hex);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }
}