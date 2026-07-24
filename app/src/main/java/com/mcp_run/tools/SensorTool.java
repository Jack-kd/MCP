package com.mcp_run.tools;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 位置/传感器工具
 * 支持 get_location / sensor_list / sensor_read
 */
public class SensorTool implements MCPTool {
    private final String action;

    public SensorTool(String action) {
        this.action = action;
    }

    @Override
    public String getName() {
        return action;
    }

    @Override
    public String getDescription() {
        switch (action) {
            case "get_location": return "获取当前设备的GPS定位信息（经纬度、海拔、精度、速度等）。需要定位权限。";
            case "sensor_list": return "列出设备上所有可用的传感器及其参数。";
            case "sensor_read": return "读取指定传感器的当前实时数值。";
            default: return "传感器/定位操作";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            if ("sensor_read".equals(action)) {
                JSONObject typeProp = new JSONObject();
                typeProp.put("type", "integer");
                typeProp.put("description", "传感器类型编号（可通过sensor_list获取），不填则读取所有传感器");
                props.put("sensor_type", typeProp);
            }
            if ("get_location".equals(action)) {
                JSONObject timeoutProp = new JSONObject();
                timeoutProp.put("type", "integer");
                timeoutProp.put("description", "获取位置的超时时间（毫秒），默认10000");
                timeoutProp.put("default", 10000);
                props.put("timeout_ms", timeoutProp);
            }

            schema.put("properties", props);
            schema.put("required", new JSONArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        JSONObject result = new JSONObject();

        switch (action) {
            case "get_location": {
                int timeoutMs = args.optInt("timeout_ms", 10000);
                LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

                if (lm == null) {
                    result.put("success", false);
                    result.put("error", "无法获取LocationManager");
                    break;
                }

                // 检查权限
                if (android.os.Build.VERSION.SDK_INT >= 23 &&
                        context.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    result.put("success", false);
                    result.put("error", "缺少定位权限（ACCESS_FINE_LOCATION），请在设置中授予");
                    break;
                }

                final CountDownLatch latch = new CountDownLatch(1);
                final JSONObject[] locResult = new JSONObject[1];

                LocationListener listener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        try {
                            JSONObject loc = new JSONObject();
                            loc.put("latitude", location.getLatitude());
                            loc.put("longitude", location.getLongitude());
                            loc.put("altitude", location.getAltitude());
                            loc.put("accuracy", location.getAccuracy());
                            loc.put("speed", location.getSpeed());
                            loc.put("bearing", location.getBearing());
                            loc.put("provider", location.getProvider());
                            loc.put("time", location.getTime());
                            locResult[0] = loc;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override
                    public void onProviderEnabled(String provider) {}
                    @Override
                    public void onProviderDisabled(String provider) {}
                };

                // 尝试获取最近的已知位置
                Location lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location lastNetwork = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if (lastGps != null) {
                    JSONObject loc = new JSONObject();
                    loc.put("latitude", lastGps.getLatitude());
                    loc.put("longitude", lastGps.getLongitude());
                    loc.put("altitude", lastGps.getAltitude());
                    loc.put("accuracy", lastGps.getAccuracy());
                    loc.put("speed", lastGps.getSpeed());
                    loc.put("bearing", lastGps.getBearing());
                    loc.put("provider", "gps");
                    loc.put("time", lastGps.getTime());
                    loc.put("source", "last_known");
                    result.put("location", loc);
                    result.put("success", true);
                } else if (lastNetwork != null) {
                    JSONObject loc = new JSONObject();
                    loc.put("latitude", lastNetwork.getLatitude());
                    loc.put("longitude", lastNetwork.getLongitude());
                    loc.put("altitude", lastNetwork.getAltitude());
                    loc.put("accuracy", lastNetwork.getAccuracy());
                    loc.put("speed", lastNetwork.getSpeed());
                    loc.put("bearing", lastNetwork.getBearing());
                    loc.put("provider", "network");
                    loc.put("time", lastNetwork.getTime());
                    loc.put("source", "last_known");
                    result.put("location", loc);
                    result.put("success", true);
                } else {
                    // 请求实时定位
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, listener);
                    boolean got = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
                    lm.removeUpdates(listener);

                    if (got && locResult[0] != null) {
                        result.put("location", locResult[0]);
                        result.put("source", "realtime");
                        result.put("success", true);
                    } else {
                        result.put("success", false);
                        result.put("error", "获取位置超时，请确保GPS已开启");
                    }
                }
                break;
            }
            case "sensor_list": {
                SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
                if (sm == null) {
                    result.put("success", false);
                    result.put("error", "设备不支持传感器");
                    break;
                }
                List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
                JSONArray sensorList = new JSONArray();
                for (Sensor s : sensors) {
                    JSONObject sensor = new JSONObject();
                    sensor.put("name", s.getName());
                    sensor.put("vendor", s.getVendor());
                    sensor.put("type", s.getType());
                    sensor.put("type_name", getSensorTypeName(s.getType()));
                    sensor.put("version", s.getVersion());
                    sensor.put("max_range", s.getMaximumRange());
                    sensor.put("resolution", s.getResolution());
                    sensor.put("power", s.getPower());
                    sensor.put("min_delay", s.getMinDelay());
                    sensorList.put(sensor);
                }
                result.put("sensors", sensorList);
                result.put("count", sensorList.length());
                result.put("success", true);
                break;
            }
            case "sensor_read": {
                int sensorType = args.optInt("sensor_type", -1);
                SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
                if (sm == null) {
                    result.put("success", false);
                    result.put("error", "设备不支持传感器");
                    break;
                }

                if (sensorType >= 0) {
                    Sensor sensor = sm.getDefaultSensor(sensorType);
                    if (sensor == null) {
                        result.put("success", false);
                        result.put("error", "传感器类型 " + sensorType + " 不可用");
                        break;
                    }
                    result.put("sensor", readSingleSensor(sm, sensor));
                } else {
                    // 读取所有传感器
                    List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
                    JSONArray readings = new JSONArray();
                    for (Sensor s : sensors) {
                        readings.put(readSingleSensor(sm, s));
                    }
                    result.put("readings", readings);
                    result.put("count", readings.length());
                }
                result.put("success", true);
                break;
            }
        }
        return result;
    }

    private JSONObject readSingleSensor(SensorManager sm, Sensor sensor) throws Exception {
        JSONObject reading = new JSONObject();
        reading.put("name", sensor.getName());
        reading.put("type", sensor.getType());
        reading.put("type_name", getSensorTypeName(sensor.getType()));

        final CountDownLatch latch = new CountDownLatch(1);
        final float[][] values = new float[1][];

        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                values[0] = event.values.clone();
                latch.countDown();
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI);
        boolean got = latch.await(2, TimeUnit.SECONDS);
        sm.unregisterListener(listener);

        if (got && values[0] != null) {
            JSONArray vals = new JSONArray();
            for (float v : values[0]) {
                vals.put(Math.round(v * 1000.0) / 1000.0);
            }
            reading.put("values", vals);
        } else {
            reading.put("values", new JSONArray());
            reading.put("note", "无法读取，传感器可能未激活");
        }
        return reading;
    }

    private String getSensorTypeName(int type) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER: return "加速度计";
            case Sensor.TYPE_MAGNETIC_FIELD: return "磁力计";
            case Sensor.TYPE_ORIENTATION: return "方向传感器";
            case Sensor.TYPE_GYROSCOPE: return "陀螺仪";
            case Sensor.TYPE_LIGHT: return "光线传感器";
            case Sensor.TYPE_PRESSURE: return "压力传感器";
            case Sensor.TYPE_TEMPERATURE: return "温度传感器";
            case Sensor.TYPE_PROXIMITY: return "接近传感器";
            case Sensor.TYPE_GRAVITY: return "重力传感器";
            case Sensor.TYPE_LINEAR_ACCELERATION: return "线性加速度";
            case Sensor.TYPE_ROTATION_VECTOR: return "旋转矢量";
            case Sensor.TYPE_RELATIVE_HUMIDITY: return "湿度传感器";
            case Sensor.TYPE_AMBIENT_TEMPERATURE: return "环境温度";
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED: return "未校准磁力计";
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED: return "未校准陀螺仪";
            case Sensor.TYPE_SIGNIFICANT_MOTION: return "显著运动";
            case Sensor.TYPE_STEP_DETECTOR: return "步数检测";
            case Sensor.TYPE_STEP_COUNTER: return "步数计数器";
            case Sensor.TYPE_HEART_RATE: return "心率传感器";
            default: return "未知(" + type + ")";
        }
    }
}