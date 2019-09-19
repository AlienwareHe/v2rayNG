package com.v2ray.ang

import android.app.Application
import android.content.Context
import com.tencent.bugly.crashreport.CrashReport
//import com.squareup.leakcanary.LeakCanary
import com.v2ray.ang.util.AngConfigManager
import me.dozen.dpreference.DPreference
import org.jetbrains.anko.defaultSharedPreferences

class AngApplication : Application() {
    companion object {
        const val PREF_LAST_VERSION = "pref_last_version"
        // 增加全局获取Context的方法，一定要注意赋值！
        var _context: Application? = null
        fun getContext(): Context {
            return _context!!
        }

    }

    var firstRun = false
        private set

    val defaultDPreference by lazy { DPreference(this, packageName + "_preferences") }

    override fun onCreate() {
        super.onCreate()
        _context = this
//        LeakCanary.install(this)

        firstRun = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0) != BuildConfig.VERSION_CODE
        if (firstRun)
            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE).apply()

        //Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        AngConfigManager.inject(this)

        // tencent bugly crash
        CrashReport.initCrashReport(applicationContext,"a8a79bbeb4",true)

    }
}
