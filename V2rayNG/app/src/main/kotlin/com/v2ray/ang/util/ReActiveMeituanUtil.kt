package com.v2ray.ang.util

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import com.v2ray.ang.backdoor.SocksServerManager.TAG
import java.io.File
import java.lang.Exception

object ReActiveMeituanUtil {

    fun runMeituanApp(context: Context,forceStart:Boolean) {
        LogCenter.log("V2RAY 保活美团开始")
        var isInstalledMeituan = false
        val installedPackages = context.packageManager.getInstalledPackages(0)
        for (installedPackage in installedPackages) {
            if ("com.sankuai.meituan".equals(installedPackage.packageName)) {
                isInstalledMeituan = true
                break
            }
        }
        if (!isInstalledMeituan) {
            Log.i(TAG, "未安装美团")
            return
        }
        // 是否直接启动
        if(forceStart){
            Handler(Looper.getMainLooper()).postDelayed({
                context.startActivity(context.packageManager.getLaunchIntentForPackage("com.sankuai.meituan"))
                LogCenter.log("美团保活：直接启动美团APP")
            }, 8 * 1000)
            return
        }

        // 根据美团的luanchTime判断美团是否需要保活
        var isMtRunning = true
        var launchTime = ""
        try{
            launchTime = File("/sdcard/ratel_white_dir/com.sankuai.meituan/launchTime.txt").readText()
        }catch (e:Exception){
            Log.i(TAG,"get meituan launchTime exception:",e)
        }
        if (TextUtils.isEmpty(launchTime)) {
            isMtRunning = false
        } else {
            // 如果时间差大于六分钟，则触发保活机制
            try {
                var time = System.currentTimeMillis() - launchTime.toLong()
                if (time > 6 * 60 * 1000) {
                    isMtRunning = false
                }
            } catch (e: Exception) {
                LogCenter.log("get meituan launchTime error:", e)
                isMtRunning = false
            }
        }

        if (isMtRunning) {
            LogCenter.log("美团保活：美团正在运行中")
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                context.startActivity(context.packageManager.getLaunchIntentForPackage("com.sankuai.meituan"))
                LogCenter.log("美团保活：成功，启动美团APP")
            }, 8 * 1000)
        }
    }
}
