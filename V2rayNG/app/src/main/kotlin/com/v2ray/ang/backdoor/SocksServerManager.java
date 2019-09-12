package com.v2ray.ang.backdoor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.v2ray.ang.AngApplication;
import com.v2ray.ang.AppConfig;
import com.v2ray.ang.dto.AngConfig;
import com.v2ray.ang.extension._ExtKt;
import com.v2ray.ang.model.OpsSocksInfo;
import com.v2ray.ang.model.TrainSocksInfo;
import com.v2ray.ang.service.V2RayVpnService;
import com.v2ray.ang.util.AngConfigManager;
import com.v2ray.ang.util.DeviceInfoUtil;
import com.v2ray.ang.util.HttpClientUtils;
import com.v2ray.ang.util.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import me.dozen.dpreference.DPreference;

import static com.v2ray.ang.AppConfig.ANG_CONFIG;

/**
 * @see com.v2ray.ang.util.Utils
 * @see AngApplication.Companion#getContext()
 * @see com.v2ray.ang.ui.MainActivity
 * @see _ExtKt#getDefaultDPreference(Context)
 */
public class SocksServerManager {

    public final static String TAG = "ALIEN_V2RAY";

    private static final Gson GSON = new Gson();

    private static DPreference defaultDPreference = _ExtKt.getDefaultDPreference(AngApplication.Companion.getContext());
    private static AngConfigManager angConfigManager = AngConfigManager.INSTANCE;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_READ_PHONE_STATE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"};

    /**
     * 清除所有不在使用中的代理服务器配置
     */
    public static void removeAllIdleServer() {
        AngConfig angConfig = angConfigManager.getConfigs();
        ArrayList<AngConfig.VmessBean> vmessBeans = angConfig.getVmess();
        if (vmessBeans == null || vmessBeans.size() <= 0) {
            return;
        }
        int activeIndex = angConfigManager.getConfigs().getIndex();
        if (activeIndex == -1) {
            angConfigManager.getConfigs().getVmess().clear();
            Log.i(TAG, "清除所有代理服务器配置：" + vmessBeans.size());
            return;
        }
        AngConfig.VmessBean activeVmess = vmessBeans.get(angConfigManager.getConfigs().getIndex());
        Log.i(TAG, "总配置数：" + vmessBeans.size() + "当前激活配置：" + activeVmess.getAddress() + ":" + activeVmess.getPort());
        String activeServerUrl = getServerUrl(activeVmess);
        List<AngConfig.VmessBean> needRemoveVmess = new ArrayList<>();
        for (int index = 0; index < vmessBeans.size(); index++) {
            AngConfig.VmessBean vmessBean = vmessBeans.get(index);
            if (getServerUrl(vmessBean).equals(activeServerUrl)) {
                // 正在使用的配置跳过
                continue;
            }
            needRemoveVmess.add(vmessBean);
        }
        angConfig.getVmess().removeAll(needRemoveVmess);
        angConfig.setIndex(0);
        Log.i(TAG, "删除" + needRemoveVmess.size() + "条socks配置:" + new Gson().toJson(needRemoveVmess));
    }

    /**
     * 根据服务器地址切换激活代理服务器
     * 1. 选中配置
     * 2. 暂停已运行的VpnService然后开启VpnService
     *
     * @param serverUrl 代理服务器Host:代理服务器Port
     */
    public static boolean switchServer(String serverUrl) {
        if (Strings.isNullOrEmpty(serverUrl)) {
            return false;
        }
        // 是否存在
        if (!getAllServerUrls().contains(serverUrl)) {
            return false;
        }
        AngConfig angConfig = angConfigManager.getConfigs();
        ArrayList<AngConfig.VmessBean> vmessBeans = angConfig.getVmess();
        // 找出所在位置
        for (int index = 0; index < vmessBeans.size(); index++) {
            AngConfig.VmessBean vmessBean = vmessBeans.get(index);
            if (serverUrl.equals(getServerUrl(vmessBean))) {
                Log.i(TAG, "找到匹配的服务器所在位置:" + index + "，" + new Gson().toJson(vmessBean));
                final int serverPos = index;
                new Thread(() -> {
                    boolean stopRes = syncStopV2Ray();
                    Log.i(TAG, "关闭V2Ray服务:" + stopRes);
                    if (angConfigManager.setActiveServer(serverPos) == 0) {
                        startV2Ray();
                    }
                }).start();
                return true;
            }
        }
        Log.i(TAG, "未找到匹配的服务器:" + serverUrl);
        return false;
    }

    /**
     * 发送消息开启V2Ray，但不保证开启完成
     */
    public static boolean startV2Ray() {
        Log.i(TAG, "启动V2Ray：" + defaultDPreference.getPrefString(ANG_CONFIG, "null"));
        new Handler(Looper.getMainLooper()).post(() -> {
            boolean start = Utils.INSTANCE.startVService(AngApplication.Companion.getContext());
            Log.i(TAG, "启动V2Ray结果：" + start);
        });
        return true;
    }

    public static boolean isV2RayVpnRunning() {
        return Utils.INSTANCE.isServiceRun(AngApplication.Companion.getContext(), V2RayVpnService.class.getName());
    }

    public static boolean syncStopV2Ray() {
        // 已经处于关闭状态
        if (!isV2RayVpnRunning()) {
            return true;
        }
        // 关闭当前运行中的V2Ray
        new Handler(Looper.getMainLooper()).post(() -> Utils.INSTANCE.stopVService(AngApplication.Companion.getContext()));
        // 等待最多十秒
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Log.i(TAG, "等待V2RayVpnService关闭失败");
            }
            if (!isV2RayVpnRunning()) {
                return true;
            }
        }
        return false;
    }


    /**
     * 从sd卡中加载socks配置并启用
     * 默认文件路径：/sdcard/ratel_white_dir/com.sankuai.meituan/socks.txt
     */
    public static void switchSocksServerFromSdFile(String socksServerFilePath) {
        if (socksServerFilePath == null || socksServerFilePath.isEmpty()) {
            socksServerFilePath = "/sdcard/ratel_white_dir/com.sankuai.meituan/socks.txt";
        }
        File file = new File(socksServerFilePath);
        try {
            List<String> socksServersJson = Files.readLines(file, Charset.forName("UTF-8"));
            if (socksServersJson == null || socksServersJson.size() == 0) {
                Log.i(TAG, "socks配置文件为空");
                return;
            }
            AngConfig.VmessBean socksBean = new Gson().fromJson(socksServersJson.get(0), AngConfig.VmessBean.class);
            registerAndActiveServer(socksBean);
        } catch (IOException e) {
            Log.e(TAG, "读取socks配置文件错误", e);
        }
    }

    public static boolean registerAndActiveServer(AngConfig.VmessBean vmessBean) {
        // 去重
        Set<String> allServerUrls = getAllServerUrls();
        if (allServerUrls.contains(getServerUrl(vmessBean))) {
            return switchServer(getServerUrl(vmessBean));
        }
        int addRes = angConfigManager.addSocksServer(vmessBean, -1);
        if (addRes != 0) {
            return false;
        }
        return switchServer(getServerUrl(vmessBean));
    }

    /**
     * @param socksServersJson socksServer配置Json集合
     */
    public static int registerSocks5Servers(List<String> socksServersJson) {
        if (socksServersJson == null || socksServersJson.size() == 0) {
            return 0;
        }
        int succNum = 0;
        Set<String> allServerHosts = getAllServerUrls();
        for (String socksServerJson : socksServersJson) {
            if (socksServerJson == null || socksServerJson.isEmpty()) {
                continue;
            }
            AngConfig.VmessBean socksBean = null;
            try {
                socksBean = new Gson().fromJson(socksServerJson, AngConfig.VmessBean.class);
            } catch (Exception e) {
                Log.e(TAG, "socksInfo不合法：" + socksServerJson, e);
            }
            if (socksBean == null) {
                continue;
            }
            if (allServerHosts.contains(getServerUrl(socksBean))) {
                // 已存在则忽略
                continue;
            }
            Log.i(TAG, "vmess bean:" + new Gson().toJson(socksBean));
            // 新增
            if (angConfigManager.addSocksServer(socksBean, -1) == 0) {
                succNum++;
            }
        }
        return succNum;
    }

    private static AngConfig.VmessBean buildSocksBean(Map socksServerInfo) {
        AngConfig.VmessBean socksServer = new AngConfig.VmessBean();
        socksServer.setAddress((String) socksServerInfo.get("address"));
        socksServer.setPort((int) socksServerInfo.get("port"));
        socksServer.setRemarks((String) socksServerInfo.get("remarks"));
        return socksServer;
    }

    public static Set<String> getAllServerUrls() {
        Set<String> urls = new HashSet<>();
        ArrayList<AngConfig.VmessBean> vmessBeans = angConfigManager.getConfigs().getVmess();
        for (AngConfig.VmessBean vmessBean : vmessBeans) {
            urls.add(getServerUrl(vmessBean));
        }
        return urls;
    }


    /**
     * 动态申请SD卡读写权限，回调需要在Activity中进行
     */
    public static void verifyStoragePermissions(Activity activity) {

        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
            // 检测READ_STATE权限
            int readStatePermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE);
            if (readStatePermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE);
            }
        } catch (Exception e) {
            Log.i(TAG, "动态申请权限失败", e);
        }
    }

    public static String getServerUrl(AngConfig.VmessBean vmessBean) {
        if (vmessBean == null) {
            return "";
        }
        return vmessBean.getAddress() + ":" + vmessBean.getPort();
    }


    /**
     * 火车票获取SOCKS代理资源
     */
    public static AngConfig.VmessBean getSocksFromTrain() {
        String resp = HttpClientUtils.get("http://l-nfsrr.t.cn8.qunar.com:2081/getSocksProxyList");
        if (TextUtils.isEmpty(resp)) {
            Log.i(TAG, "the socks from train is empty");
            return null;
        }
        List<TrainSocksInfo> trainSocksInfos = GSON.fromJson(resp, TypeToken.getParameterized(List.class, TrainSocksInfo.class).getType());
        if (trainSocksInfos == null || trainSocksInfos.size() == 0) {
            Log.i(TAG, "the socks from train is null");
            return null;
        }
        TrainSocksInfo trainSocksInfo = trainSocksInfos.get(new Random().nextInt(trainSocksInfos.size()));
        if (trainSocksInfo == null || TextUtils.isEmpty(trainSocksInfo.getProxyIp()) || trainSocksInfo.getProxyPort() <= 0) {
            Log.i(TAG, "the socks from train is invalid");
            return null;
        }
        AngConfig.VmessBean vmessBean = new AngConfig.VmessBean();
        vmessBean.setPort(trainSocksInfo.getProxyPort());
        vmessBean.setAddress(trainSocksInfo.getProxyIp());
        vmessBean.setRemarks(trainSocksInfo.getExportIp());
        return vmessBean;
    }


    /**
     * 从OPS获取SOCKS代理资源
     */
    public static AngConfig.VmessBean getSocksFromOps() {
        Map<String, String> params = new HashMap<>();
        params.put("apptype", "noLoginMT");
        params.put("deviceId", DeviceInfoUtil.getSerial());
        // 代理线路渠道，4国内 5台湾 6美国
        params.put("proxyType", "4");
        params.put("type", "socks");
        String resp = HttpClientUtils.post("http://controlips.corp.qunar.com/ipgetonce.do", params);
        if (TextUtils.isEmpty(resp)) {
            Log.i(TAG, "http://controlips.corp.qunar.com/ipgetonce.do 返回数据为空");
            return null;
        }
        Map data = GSON.fromJson(resp, Map.class);
        if (data.get("data") == null) {
            Log.i(TAG, "http://controlips.corp.qunar.com/ipgetonce.do 返回数据为空" + resp);
            return null;
        }
        OpsSocksInfo socksInfo = GSON.fromJson(GSON.toJson(data.get("data")), OpsSocksInfo.class);
        if (socksInfo == null || !socksInfo.getEnable() || TextUtils.isEmpty(socksInfo.getHost()) || socksInfo.getPort() == null) {
            Log.i(TAG, "http://controlips.corp.qunar.com/ipgetonce.do 返回socks信息不合法：" + resp);
            return null;
        }
        AngConfig.VmessBean vmessBean = new AngConfig.VmessBean();
        vmessBean.setRemarks(socksInfo.getHost());
        vmessBean.setAddress(socksInfo.getHost());
        vmessBean.setPort(socksInfo.getPort());
        return vmessBean;
    }

}
