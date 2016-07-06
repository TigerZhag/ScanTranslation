package com.readboy.scantranslate.Utils;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * @author Zhang shixin
 * @date 16-7-6.
 */
public class HardwareUtil {

    //硬件是否支持相机
    public static boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            //this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }
}
