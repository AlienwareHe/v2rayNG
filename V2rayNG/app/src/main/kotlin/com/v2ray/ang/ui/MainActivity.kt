package com.v2ray.ang.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import com.tbruyelle.rxpermissions.RxPermissions
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.backdoor.SocksServerManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.service.AutoChangeServerThread
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.AngConfigManager.configs
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.V2rayConfigUtil
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.lang.ref.SoftReference
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        private const val REQUEST_CODE_VPN_PREPARE = 0
        private const val REQUEST_SCAN = 1
        private const val REQUEST_FILE_CHOOSER = 2
        private const val REQUEST_SCAN_URL = 3
    }

    var isRunning = false
        set(value) {
            field = value
            adapter.changeable = !value
            if (value) {
                fab.imageResource = R.drawable.ic_v
                tv_test_state.text = getString(R.string.connection_connected)
            } else {
                fab.imageResource = R.drawable.ic_v_idle
                tv_test_state.text = getString(R.string.connection_not_connected)
            }
            hideCircle()
        }

    private val adapter by lazy { MainRecyclerAdapter(this) }
    private var mItemTouchHelper: ItemTouchHelper? = null

    // 权限回调
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults == null || grantResults.isEmpty()) {
            return
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i(SocksServerManager.TAG, "读写权限获取成功")
        } else {
            // 否则重试一次
            SocksServerManager.verifyStoragePermissions(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // do some external thing
        // 1.申请SD卡读写权限
        SocksServerManager.verifyStoragePermissions(this)
        // 2.启动AppManageService
        val intent = Intent()
        intent.setClassName(this, "com.v2ray.ang.service.AppManageService")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        }else{
            startService(intent)
        }

        // end do some external thing
        setContentView(R.layout.activity_main)
        title = getString(R.string.title_server)
        setSupportActionBar(toolbar)

        fab.setOnClickListener {
            if (isRunning) {
                Utils.stopVService(this)
            } else {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    startActivityForResult(intent, REQUEST_CODE_VPN_PREPARE)
                }
            }
        }
        layout_test.setOnClickListener {
            if (isRunning) {
                val socksPort = 10808//Utils.parseInt(defaultDPreference.getPrefString(SettingsActivity.PREF_SOCKS_PORT, "10808"))

                tv_test_state.text = getString(R.string.connection_test_testing)
                doAsync {
                    val result = Utils.testConnection(this@MainActivity, socksPort)
                    uiThread {
                        tv_test_state.text = Utils.getEditable(result)
                    }
                }
            } else {
//                tv_test_state.text = getString(R.string.connection_test_fail)
            }
        }

        recycler_view.setHasFixedSize(true)
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(recycler_view)


        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
        nav_view.setNavigationItemSelectedListener(this)

        /**
         * 默认启动一个SOCKS代理
         */
        Handler().post { AutoChangeServerThread.autoChangeServer() }

        /**
         * 判断是否需要跳回原APP
         */
        val jumpSourceIntent = getIntent();
        Log.i("ALIEN_MEITUAN", "intent is null:" + (jumpSourceIntent == null))
        if (jumpSourceIntent == null) {
            return
        }
        val bundle: Bundle? = jumpSourceIntent.extras ?: return
        val originPkg = bundle?.getString("originPkg")
        val originCls = bundle?.getString("originCls")
        if (!TextUtils.isEmpty(originPkg) && !TextUtils.isEmpty(originCls)) {
            val jumpIntent = Intent()
            val componentName = ComponentName(originPkg, originCls)
            jumpIntent.component = componentName
            this.startActivity(jumpIntent)
        }

    }

    fun startV2Ray() {
        if (AngConfigManager.configs.index < 0) {
            return
        }
        showCircle()
//        toast(R.string.toast_services_start)
        if (!Utils.startVService(this)) {
            hideCircle()
        }
    }

    override fun onStart() {
        super.onStart()
        isRunning = false

//        val intent = Intent(this.applicationContext, V2RayVpnService::class.java)
//        intent.`package` = AppConfig.ANG_PACKAGE
//        bindService(intent, mConnection, BIND_AUTO_CREATE)

        mMsgReceive = ReceiveMessageHandler(this@MainActivity)
        registerReceiver(mMsgReceive, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY))
        MessageUtil.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onStop() {
        super.onStop()
        if (mMsgReceive != null) {
            unregisterReceiver(mMsgReceive)
            mMsgReceive = null
        }
    }

    public override fun onResume() {
        super.onResume()
        adapter.updateConfigList()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_VPN_PREPARE ->
                if (resultCode == RESULT_OK) {
                    startV2Ray()
                }
            REQUEST_SCAN ->
                if (resultCode == RESULT_OK) {
                    importBatchConfig(data?.getStringExtra("SCAN_RESULT"))
                }
            REQUEST_FILE_CHOOSER -> {
                if (resultCode == RESULT_OK) {
                    val uri = data!!.data
                    readContentFromUri(uri)
                }
            }
            REQUEST_SCAN_URL ->
                if (resultCode == RESULT_OK) {
                    importConfigCustomUrl(data?.getStringExtra("SCAN_RESULT"))
                }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    /**
     * 菜单项点击触发事件
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val b = when (item.itemId) {
            R.id.import_qrcode -> {
                importQRcode(REQUEST_SCAN)
                true
            }
            R.id.import_clipboard -> {
                importClipboard()
                true
            }
            R.id.import_manually_vmess -> {
                startActivity<ServerActivity>("position" to -1, "isRunning" to isRunning)
                adapter.updateConfigList()
                true
            }
            R.id.import_manually_ss -> {
                startActivity<Server3Activity>("position" to -1, "isRunning" to isRunning)
                adapter.updateConfigList()
                true
            }
            R.id.import_manually_socks -> {
                startActivity<Server4Activity>("position" to -1, "isRunning" to isRunning)
                adapter.updateConfigList()
                true
            }
            // 清除未激活状态的服务器配置
            R.id.remove_all_idel_servers -> {
                SocksServerManager.removeAllIdleServer()
                refreshConfigsList()
                true
            }
            // 自动切换SOCKS代理
            R.id.auto_change_socks_servers -> {
                Thread(Runnable { AutoChangeServerThread.autoChangeServer(); }).start()
                // 刷新当前页面
                refreshConfigsList()
                true
            }
            // 切换SD卡中指定服务器配置
            R.id.switch_socks_from_sdcard -> {
                SocksServerManager.switchSocksServerFromSdFile("")
                refreshConfigsList()
                true
            }
            R.id.import_config_custom_clipboard -> {
                importConfigCustomClipboard()
                true
            }
            R.id.import_config_custom_local -> {
                importConfigCustomLocal()
                true
            }
            R.id.import_config_custom_url -> {
                importConfigCustomUrlClipboard()
                true
            }
            R.id.import_config_custom_url_scan -> {
                importQRcode(REQUEST_SCAN_URL)
                true
            }

            //        R.id.sub_setting -> {
            //            startActivity<SubSettingActivity>()
            //            true
            //        }

            R.id.sub_update -> {
                importConfigViaSub()
                true
            }

            R.id.export_all -> {
                if (AngConfigManager.shareAll2Clipboard() == 0) {
                    toast(R.string.toast_success)
                } else {
                    toast(R.string.toast_failure)
                }
                true
            }

            R.id.ping_all -> {
                for (k in 0 until configs.vmess.count()) {
                    configs.vmess[k].testResult = ""
                    adapter.updateConfigList()
                }
                for (k in 0 until configs.vmess.count()) {
                    if (configs.vmess[k].configType != AppConfig.EConfigType.Custom) {
                        doAsync {
                            configs.vmess[k].testResult = Utils.tcping(configs.vmess[k].address, configs.vmess[k].port)
                            uiThread {
                                adapter.updateSelectedItem(k)
                            }
                        }
                    }
                }
                true
            }

            //        R.id.settings -> {
            //            startActivity<SettingsActivity>("isRunning" to isRunning)
            //            true
            //        }
            //        R.id.logcat -> {
            //            startActivity<LogcatActivity>()
            //            true
            //        }
            else -> super.onOptionsItemSelected(item)
        }
        return b
    }


    /**
     * import config from qrcode
     */
    fun importQRcode(requestCode: Int): Boolean {
//        try {
//            startActivityForResult(Intent("com.google.zxing.client.android.SCAN")
//                    .addCategory(Intent.CATEGORY_DEFAULT)
//                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), requestCode)
//        } catch (e: Exception) {
        RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        startActivityForResult<ScannerActivity>(requestCode)
                    else
                        toast(R.string.toast_permission_denied)
                }
//        }
        return true
    }

    /**
     * import config from clipboard
     */
    fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importBatchConfig(server: String?, subid: String = "") {
        val count = AngConfigManager.importBatchConfig(server, subid)
        if (count > 0) {
            toast(R.string.toast_success)
            adapter.updateConfigList()
        } else {
            toast(R.string.toast_failure)
        }
    }

    fun importConfigCustomClipboard()
            : Boolean {
        try {
            val configText = Utils.getClipboard(this)
            if (TextUtils.isEmpty(configText)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(configText)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from local config file
     */
    fun importConfigCustomLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importConfigCustomUrlClipboard()
            : Boolean {
        try {
            val url = Utils.getClipboard(this)
            if (TextUtils.isEmpty(url)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            return importConfigCustomUrl(url)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from url
     */
    fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            doAsync {
                val configText = URL(url).readText()
                uiThread {
                    importCustomizeConfig(configText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * import config from sub
     */
    fun importConfigViaSub()
            : Boolean {
        try {
            toast(R.string.title_sub_update)
            val subItem = AngConfigManager.configs.subItem
            for (k in 0 until subItem.count()) {
                if (TextUtils.isEmpty(subItem[k].id)
                        || TextUtils.isEmpty(subItem[k].remarks)
                        || TextUtils.isEmpty(subItem[k].url)
                ) {
                    continue
                }
                val id = subItem[k].id
                val url = subItem[k].url
                if (!Utils.isValidUrl(url)) {
                    continue
                }
                Log.d("Main", url)
                doAsync {
                    val configText = URL(url).readText()
                    uiThread {
                        importBatchConfig(Utils.decode(configText), id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.title_file_chooser)),
                    REQUEST_FILE_CHOOSER)
        } catch (ex: android.content.ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        RxPermissions(this)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe {
                    if (it) {
                        try {
                            val inputStream = contentResolver.openInputStream(uri)
                            val configText = inputStream.bufferedReader().readText()
                            importCustomizeConfig(configText)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else
                        toast(R.string.toast_permission_denied)
                }
    }

    /**
     * import customize config
     */
    fun importCustomizeConfig(server: String?) {
        if (server == null) {
            return
        }
        if (!V2rayConfigUtil.isValidConfig(server)) {
            toast(R.string.toast_config_file_invalid)
            return
        }
        val resId = AngConfigManager.importCustomizeConfig(server)
        if (resId > 0) {
            toast(resId)
        } else {
            toast(R.string.toast_success)
            adapter.updateConfigList()
        }
    }

//    val mConnection = object : ServiceConnection {
//        override fun onServiceDisconnected(name: ComponentName?) {
//        }
//
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            sendMsg(AppConfig.MSG_REGISTER_CLIENT, "")
//        }
//    }

    private
    var mMsgReceive: BroadcastReceiver? = null

    private class ReceiveMessageHandler(activity: MainActivity) : BroadcastReceiver() {
        internal var mReference: SoftReference<MainActivity> = SoftReference(activity)
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val activity = mReference.get()
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    activity?.isRunning = true
                }
                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    activity?.isRunning = false
                }
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    activity?.toast(R.string.toast_services_success)
                    activity?.isRunning = true
                }
                AppConfig.MSG_STATE_START_FAILURE -> {
                    activity?.toast(R.string.toast_services_failure)
                    activity?.isRunning = false
                }
                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    activity?.isRunning = false
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun showCircle() {
        fabProgressCircle?.show()
    }

    fun hideCircle() {
        try {
            Observable.timer(300, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        if (fabProgressCircle.isShown) {
                            fabProgressCircle.hide()
                        }
                    }
        } catch (e: Exception) {
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            //R.id.server_profile -> activityClass = MainActivity::class.java
            R.id.sub_setting -> {
                startActivity<SubSettingActivity>()
            }
            R.id.settings -> {
                startActivity<SettingsActivity>("isRunning" to isRunning)
            }
            R.id.feedback -> {
                Utils.openUri(this, AppConfig.v2rayNGIssues)
            }
            R.id.promotion -> {
                Utils.openUri(this, AppConfig.promotionUrl)
            }
            R.id.logcat -> {
                startActivity<LogcatActivity>()
            }
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    fun refreshConfigsList() {
        if (adapter == null) {
            return
        }
        adapter.updateConfigList()
    }
}