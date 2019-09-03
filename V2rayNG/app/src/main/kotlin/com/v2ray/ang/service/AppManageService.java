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

    private IBinder binder = new IAppManageService.Stub() {
        @Override
        public boolean switchSocksServer(String socksInfoJson) throws RemoteException {
            if (Strings.isNullOrEmpty(socksInfoJson)) {
                return false;
            }
            AngConfig.VmessBean vmessBean = null;
            try {
                vmessBean = new Gson().fromJson(socksInfoJson, AngConfig.VmessBean.class);
            } catch (Exception e) {
                Log.e(TAG, "socksInfoJson不合法！", e);
                return false;
            }
            if(vmessBean == null){
                return false;
            }
            return SocksServerManager.registerAndActiveServer(vmessBean);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        Log.i(TAG,"V2Ray Manage Service启动成功");
    }

    @Override
    public void onDestroy() {
        Log.i(TAG,"V2Ray Manage Service销毁成功");
    }
}
