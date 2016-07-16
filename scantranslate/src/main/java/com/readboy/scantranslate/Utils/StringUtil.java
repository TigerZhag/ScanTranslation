package com.readboy.scantranslate.Utils;

/**
 * @author Zhang shixin
 * @date 16-7-15.
 */
public class StringUtil {
    public static String filterAlphabet(String alph) {
        alph = alph.replaceAll("[^(A-Za-z)]", "");
        return alph;
    }
}
