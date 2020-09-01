package com.v2ray.ang.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.v2ray.ang.IAppManageService;
import com.v2ray.ang.R;
import com.v2ray.ang.backdoor.SocksServerManager;
import com.v2ray.ang.dto.AngConfig;
import com.v2ray.ang.util.LogCenter;
import com.v2ray.ang.util.ReActiveMeituanUtil;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.v2ray.ang.backdoor.SocksServerManager.TAG;

public class AppManageService extends Service {

    private static final AtomicBoolean isReActiveTaskRunning = new AtomicBoolean(false);

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
        LogCenter.log("V2Ray Manage Service启动成功");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String NOTIFICATION_CHANNEL_ID = "V2ray.app.manage";
            String channelName = "v2ray_app_manage_service";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
            chan.setLightColor(Color.DKGRAY);
            chan.setImportance(NotificationManager.IMPORTANCE_NONE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
            Notification notification = notificationBuilder.setOngoing(true)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("VPN管理服务")
                    .setOngoing(true)
                    .setPriority(NotificationManager.IMPORTANCE_MIN)
                    .build();
            startForeground(2, notification);
        } else {
            // 前台服务
            Notification notification = new Notification.Builder(this)
                    .setContentTitle("VPN管理服务")//标题
                    .setContentText("运行中...")//内容
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.mipmap.ic_launcher)//小图标一定需要设置,如果是普通通知,不设置必然报错
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                    .build();

            startForeground(2, notification);
        }

        // 定时运行保活美团
//        if (isReActiveTaskRunning.compareAndSet(false, true)) {
//            new ScheduledThreadPoolExecutor(1).scheduleWithFixedDelay(() -> {
//                Log.i(TAG, "保活定时任务执行");
//                ReActiveMeituanUtil.INSTANCE.runMeituanApp(this,false);
//            }, 60 * 1000, 60 * 1000, TimeUnit.MILLISECONDS);
//        }
    }

    @Override
    public void onDestroy() {
        LogCenter.log("V2Ray Manage Service销毁成功");
        stopForeground(true);
        super.onDestroy();
    }
}
