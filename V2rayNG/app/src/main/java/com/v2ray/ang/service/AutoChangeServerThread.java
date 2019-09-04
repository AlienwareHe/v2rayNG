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

    @Override
    public void run() {
        Log.i(TAG,"自动切换代理服务器线程启动！");
        // schedule任务结束后才会开始计时
        new Timer("autoChangeServer").schedule(new TimerTask() {
            @Override
            public void run() {
                autoChangeServer();
            }
        }, 1000, CHANGE_INTERVAL);
    }

    public void autoChangeServer() {
        AngConfig.VmessBean server = SocksServerManager.getSocksFromOps();
        if(server == null){
            Log.i(TAG,"获取代理服务器信息失败");
            return;
        }
        Log.i(TAG,"自动切换代理动作执行："+new Gson().toJson(server));
        SocksServerManager.registerAndActiveServer(server);
    }
}
