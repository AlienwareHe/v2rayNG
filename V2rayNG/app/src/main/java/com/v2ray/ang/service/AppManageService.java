package com.v2ray.ang.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.v2ray.ang.IAppManageService;
import com.v2ray.ang.backdoor.SocksServerManager;
import com.v2ray.ang.dto.AngConfig;

import static com.v2ray.ang.backdoor.SocksServerManager.TAG;

public class AppManageService extends Service {


    /**
     * 提供AIDL远程服务
     */
    private IBinder binder = new IAppManageService.Stub() {
        @Override
        public boolean switchSocksServer(String serverInfoJson) throws RemoteException {
            if (Strings.isNullOrEmpty(serverInfoJson)) {
                return false;
            }
            Log.i(TAG, "切换代理服务器:" + serverInfoJson);
            AngConfig.VmessBean vmessBean = null;
            try {
                vmessBean = new Gson().fromJson(serverInfoJson, AngConfig.VmessBean.class);
            } catch (Exception e) {
                Log.e(TAG, "socksInfoJson不合法！", e);
                return false;
            }
            if (vmessBean == null) {
                return false;
            }
            return SocksServerManager.registerAndActiveServer(vmessBean);
        }

        @Override
        public boolean autoSwitchSocksServer() throws RemoteException {
            // 异步执行，因为关闭服务时需要等待几秒
            new Thread(AutoChangeServerThread::autoChangeServer).start();
            return true;
        }
    };


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "V2Ray Manage Service启动成功");
        // 启动定时切换代理
//        new AutoChangeServerThread().start();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "V2Ray Manage Service销毁成功");
    }
}
