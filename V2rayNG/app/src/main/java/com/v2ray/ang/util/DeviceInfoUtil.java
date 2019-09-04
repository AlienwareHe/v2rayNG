package com.v2ray.ang.util;

import android.annotation.SuppressLint;
import android.os.Build;
import android.text.TextUtils;

public class DeviceInfoUtil {

    private static String serial = "";

    @SuppressLint("MissingPermission")
    public static String getSerial(){
        if(!TextUtils.isEmpty(serial)){
            return serial;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                serial = Build.getSerial();
            } catch (Exception e) {
                //ignore
            }
        }
        if (serial == null) {
            try {
                serial = Build.SERIAL;
            } catch (Exception e) {
                //ignore
            }
        }
        return serial;
    }
}
