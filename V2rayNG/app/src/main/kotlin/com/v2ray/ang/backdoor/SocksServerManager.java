package com.v2ray.ang.backdoor;

import android.util.Log;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.v2ray.ang.AngApplication;
import com.v2ray.ang.AppConfig;
import com.v2ray.ang.dto.AngConfig;
import com.v2ray.ang.extension._ExtKt;
import com.v2ray.ang.util.AngConfigManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.dozen.dpreference.DPreference;

/**
 * @see com.v2ray.ang.util.Utils
 * @see AngConfigManager
 * @see com.v2ray.ang.ui.MainActivity
 */
public class SocksServerManager {

    public final static String TAG = "ALIEN_V2RAY";

    private static DPreference defaultDPreference = _ExtKt.getDefaultDPreference(AngApplication.Companion.getContext());
    private static AngConfigManager angConfigManager = AngConfigManager.INSTANCE;

    /**
     * 清除所有不在使用中的代理服务器配置
     */
    public static void removeAllIdleServer() {
        AngConfig angConfig = angConfigManager.getConfigs();
        ArrayList<AngConfig.VmessBean> vmessBeans = angConfig.getVmess();
        String activeServerHost = defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG_DOMAIN, "");
        if ("".equals(activeServerHost)) {
            Log.i(TAG, "当前无激活socks配置");
        }
        for (int index = 0; index < vmessBeans.size(); index++) {
            AngConfig.VmessBean vmessBean = vmessBeans.get(index);
            if (vmessBean.getAddress().equals(activeServerHost)) {
                // 正在使用的配置跳过
                continue;
            }
            angConfigManager.removeServer(index);
            Log.i(TAG, "删除socks配置:" + new Gson().toJson(vmessBean));
        }
    }

    /**
     * 根据服务器地址切换激活代理服务器
     *
     * @param serverHost 代理服务器Host
     */
    public static boolean switchServer(String serverHost) {
        // 找出所在位置
        AngConfig angConfig = angConfigManager.getConfigs();
        ArrayList<AngConfig.VmessBean> vmessBeans = angConfig.getVmess();
        for (int index = 0; index < vmessBeans.size(); index++) {
            AngConfig.VmessBean vmessBean = vmessBeans.get(index);
            if (vmessBean.getAddress().equals(serverHost)) {
                Log.i(TAG, "找到匹配的服务器：" + new Gson().toJson(vmessBean));
                return angConfigManager.setActiveServer(index) == 0;
            }
        }
        Log.i(TAG, "未找到匹配的服务器:" + serverHost);
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
        Set<String> allServerHosts = getAllServerHosts();
        if (allServerHosts.contains(vmessBean.getAddress())) {
            return switchServer(vmessBean.getAddress());
        }
        int addRes = angConfigManager.addSocksServer(vmessBean, -1);
        if (addRes != 0) {
            return false;
        }
        return switchServer(vmessBean.getAddress());
    }

    /**
     * @param socksServersJson socksServer配置Json集合
     */
    public static int registerSocks5Servers(List<String> socksServersJson) {
        if (socksServersJson == null || socksServersJson.size() == 0) {
            return 0;
        }
        int succNum = 0;
        Set<String> allServerHosts = getAllServerHosts();
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
            if (allServerHosts.contains(socksBean.getAddress())) {
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

    public static Set<String> getAllServerHosts() {
        Set<String> hosts = new HashSet<>();
        ArrayList<AngConfig.VmessBean> vmessBeans = angConfigManager.getConfigs().getVmess();
        for (AngConfig.VmessBean vmessBean : vmessBeans) {
            hosts.add(vmessBean.getAddress());
        }
        return hosts;
    }

}
