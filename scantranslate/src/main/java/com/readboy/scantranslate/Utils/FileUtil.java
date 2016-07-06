package com.readboy.scantranslate.Utils;

import java.io.File;

/**
 * @author Zhang shixin
 * @date 16-7-6.
 */
public class FileUtil {
    //OCR语言包是否存在
    public static boolean dataIsExists(String string) {
        File f_chi = new File(string + "libchi_sim.so");
        if (f_chi.exists()) {
            if (f_chi.length() != 39973777) {
                f_chi.delete();
                return false;
            }
        } else {
            return false;
        }

        File f_eng = new File(string + "libeng.so");
        if (f_eng.exists()) {
            if (f_eng.length() != 21876572) {
                f_eng.delete();
                return false;
            }
        } else {
            return false;
        }

        File f_py = new File(string + "libpinyin.so");
        if (f_py.exists()) {
            if (f_py.length() != 412034) {
                f_py.delete();
                return false;
            }
        } else {
            return false;
        }

        return true;
    }
}
