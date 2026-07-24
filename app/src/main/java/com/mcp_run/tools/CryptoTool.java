package com.mcp_run.tools;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加解密工具
 * 支持 hash / encrypt_aes / decrypt_aes / random_bytes
 */
public class CryptoTool implements MCPTool {
    private final String action;

    public CryptoTool(String action) {
        this.action = action;
    }

    @Override
    public String getName() {
        return action;
    }

    @Override
    public String getDescription() {
        switch (action) {
            case "hash": return "计算文本或文件的哈希值，支持MD5/SHA1/SHA256/SHA512算法。";
            case "encrypt_aes": return "使用AES-256-CBC加密文本，返回Base64编码的密文。";
            case "decrypt_aes": return "解密AES-256-CBC加密的Base64密文，返回明文。";
            case "random_bytes": return "生成指定长度的安全随机字节，返回Base64和Hex格式。";
            default: return "加解密操作";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            switch (action) {
                case "hash": {
                    JSONObject algoProp = new JSONObject();
                    algoProp.put("type", "string");
                    algoProp.put("description", "哈希算法: md5/sha1/sha256/sha512");
                    algoProp.put("enum", new JSONArray().put("md5").put("sha1").put("sha256").put("sha512"));
                    algoProp.put("default", "sha256");
                    props.put("algorithm", algoProp);

                    JSONObject textProp = new JSONObject();
                    textProp.put("type", "string");
                    textProp.put("description", "要计算哈希的文本（与file_path二选一）");
                    props.put("text", textProp);

                    JSONObject fileProp = new JSONObject();
                    fileProp.put("type", "string");
                    fileProp.put("description", "要计算哈希的文件路径（与text二选一）");
                    props.put("file_path", fileProp);
                    break;
                }
                case "encrypt_aes": {
                    JSONObject keyProp = new JSONObject();
                    keyProp.put("type", "string");
                    keyProp.put("description", "AES密钥（16/24/32字节字符串，对应AES-128/192/256）");
                    props.put("key", keyProp);

                    JSONObject ivProp = new JSONObject();
                    ivProp.put("type", "string");
                    ivProp.put("description", "初始化向量IV（16字节字符串），不填则自动生成随机IV并拼接在密文前");
                    props.put("iv", ivProp);

                    JSONObject textProp = new JSONObject();
                    textProp.put("type", "string");
                    textProp.put("description", "要加密的明文");
                    props.put("plaintext", textProp);
                    break;
                }
                case "decrypt_aes": {
                    JSONObject keyProp = new JSONObject();
                    keyProp.put("type", "string");
                    keyProp.put("description", "AES密钥（16/24/32字节字符串）");
                    props.put("key", keyProp);

                    JSONObject ivProp = new JSONObject();
                    ivProp.put("type", "string");
                    ivProp.put("description", "初始化向量IV（16字节字符串），加密时使用了随机IV则需要此参数");
                    props.put("iv", ivProp);

                    JSONObject textProp = new JSONObject();
                    textProp.put("type", "string");
                    textProp.put("description", "Base64编码的密文");
                    props.put("ciphertext", textProp);
                    break;
                }
                case "random_bytes": {
                    JSONObject lenProp = new JSONObject();
                    lenProp.put("type", "integer");
                    lenProp.put("description", "生成的随机字节数，默认32");
                    lenProp.put("default", 32);
                    props.put("length", lenProp);
                    break;
                }
            }

            schema.put("properties", props);
            JSONArray required = new JSONArray();
            if ("hash".equals(action)) {
                // text or file_path is required, but optional in schema
            } else if ("encrypt_aes".equals(action)) {
                required.put("key").put("plaintext");
            } else if ("decrypt_aes".equals(action)) {
                required.put("key").put("ciphertext");
            }
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
            case "hash": {
                String algorithm = args.optString("algorithm", "sha256").toLowerCase();
                String text = args.optString("text", null);
                String filePath = args.optString("file_path", null);

                byte[] input;
                if (text != null) {
                    input = text.getBytes("UTF-8");
                } else if (filePath != null) {
                    java.io.File f = new java.io.File(filePath);
                    if (!f.exists()) throw new Exception("文件不存在: " + filePath);
                    input = new byte[(int) f.length()];
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                        fis.read(input);
                    }
                } else {
                    throw new Exception("请提供 text 或 file_path 参数");
                }

                MessageDigest md = MessageDigest.getInstance(algorithm.toUpperCase().replace("SHA", "SHA-"));
                byte[] hash = md.digest(input);
                result.put("algorithm", algorithm);
                result.put("hash", bytesToHex(hash));
                result.put("byte_length", input.length);
                result.put("success", true);
                break;
            }
            case "encrypt_aes": {
                String key = args.getString("key");
                String plaintext = args.getString("plaintext");
                String ivStr = args.optString("iv", null);

                byte[] keyBytes = key.getBytes("UTF-8");
                // Ensure key length: 16, 24, or 32 bytes
                if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                    keyBytes = Arrays.copyOf(keyBytes, 32); // pad to 32
                }

                SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                byte[] iv;
                boolean randomIv = false;

                if (ivStr != null && !ivStr.isEmpty()) {
                    iv = ivStr.getBytes("UTF-8");
                    if (iv.length != 16) iv = Arrays.copyOf(iv, 16);
                } else {
                    iv = new byte[16];
                    new SecureRandom().nextBytes(iv);
                    randomIv = true;
                }

                IvParameterSpec ivSpec = new IvParameterSpec(iv);
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
                byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));

                String ciphertext;
                if (randomIv) {
                    byte[] combined = new byte[16 + encrypted.length];
                    System.arraycopy(iv, 0, combined, 0, 16);
                    System.arraycopy(encrypted, 0, combined, 16, encrypted.length);
                    ciphertext = Base64.encodeToString(combined, Base64.NO_WRAP);
                    result.put("iv", bytesToHex(iv));
                } else {
                    ciphertext = Base64.encodeToString(encrypted, Base64.NO_WRAP);
                    result.put("iv", bytesToHex(iv));
                }

                result.put("ciphertext", ciphertext);
                result.put("plaintext_length", plaintext.length());
                result.put("ciphertext_length", ciphertext.length());
                result.put("success", true);
                break;
            }
            case "decrypt_aes": {
                String key = args.getString("key");
                String ciphertext = args.getString("ciphertext");
                String ivStr = args.optString("iv", null);

                byte[] keyBytes = key.getBytes("UTF-8");
                if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                    keyBytes = Arrays.copyOf(keyBytes, 32);
                }

                SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

                byte[] encryptedData = Base64.decode(ciphertext, Base64.DEFAULT);
                byte[] iv;

                if (ivStr != null && !ivStr.isEmpty()) {
                    iv = ivStr.getBytes("UTF-8");
                    if (iv.length != 16) iv = Arrays.copyOf(iv, 16);
                } else {
                    // Try to extract IV from first 16 bytes
                    if (encryptedData.length < 16) throw new Exception("密文太短，无法提取IV");
                    iv = new byte[16];
                    System.arraycopy(encryptedData, 0, iv, 0, 16);
                    encryptedData = Arrays.copyOfRange(encryptedData, 16, encryptedData.length);
                }

                IvParameterSpec ivSpec = new IvParameterSpec(iv);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
                byte[] decrypted = cipher.doFinal(encryptedData);

                String plaintext = new String(decrypted, "UTF-8");
                result.put("plaintext", plaintext);
                result.put("plaintext_length", plaintext.length());
                result.put("success", true);
                break;
            }
            case "random_bytes": {
                int length = args.optInt("length", 32);
                if (length < 1 || length > 1024) throw new Exception("长度必须在1-1024之间");

                byte[] random = new byte[length];
                new SecureRandom().nextBytes(random);

                result.put("hex", bytesToHex(random));
                result.put("base64", Base64.encodeToString(random, Base64.NO_WRAP));
                result.put("byte_length", length);
                result.put("success", true);
                break;
            }
        }
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}