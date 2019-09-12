package com.v2ray.ang.service;

import android.util.Log;

import com.google.gson.Gson;
import com.v2ray.ang.backdoor.SocksServerManager;
import com.v2ray.ang.dto.AngConfig;

import java.util.Timer;
import java.util.TimerTask;

import static com.v2ray.ang.backdoor.SocksServerManager.TAG;

public class AutoChangeServerThread extends Thread {

    /**
     * 变更时间间隔
     */
    private static final int CHANGE_INTERVAL = 5 * 60 * 1000;

    private static int SERVER_INDEX = 0;
    private static int SERVER_TOTAL_NUM = 2;


    @Override
    public void run() {
        Log.i(TAG, "自动切换代理服务器线程启动！");
        // schedule任务结束后才会开始计时
        new Timer("autoChangeServer").schedule(new TimerTask() {
            @Override
            public void run() {
                SocksServerManager.removeAllIdleServer();
                autoChangeServer();
            }
        }, 1000, CHANGE_INTERVAL);
    }

    /**
     * 现在socks代理资源有两个来源，轮流切换
     */
    public static synchronized void autoChangeServer() {
        AngConfig.VmessBean server = null;
        Log.i(TAG, "从OPS获取代理资源");
        server = SocksServerManager.getSocksFromOps();
//        switch (SERVER_INDEX++ % SERVER_TOTAL_NUM) {
//            case 0:
//                Log.i(TAG,"从OPS获取代理资源");
//                server = SocksServerManager.getSocksFromOps();
//                break;
//            case 1:
//                Log.i(TAG,"从Train获取代理资源");
//                server = SocksServerManager.getSocksFromTrain();
//                break;
//            default:
//                break;
//        }

        if (server == null) {
            Log.i(TAG, "获取代理服务器信息失败");
            return;
        }
        Log.i(TAG, "自动切换代理动作执行：" + new Gson().toJson(server));
        // TODO 代理信息记录
        // 清空并执行切换逻辑
        SocksServerManager.removeAllIdleServer();
        SocksServerManager.registerAndActiveServer(server);
    }
}
