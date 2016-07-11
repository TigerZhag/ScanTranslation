package com.readboy.scantranslate.Ocr;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.readboy.scantranslate.Utils.FileUtil;

import java.io.File;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * @author Zhang shixin
 * @date 16-7-6.
 */
public class OcrWorker {
    private TessBaseAPI baseApi = new TessBaseAPI();
//
//    public static int USE_ALL_MODE  = TessBaseAPI.USE_ALL;//0
//    public static int ENG_CHI_MODE  = TessBaseAPI.CHI_ENG;//3
//    public static int ENG_ONLY_MODE = TessBaseAPI.ENG_ONLY;//2
//    public static int CHI_ONLY_MODE = TessBaseAPI.CHI_ONLY;//1

    public static final String OCR_PATH_SYSTEM = "/system/lib/";
    public static final String OCR_PATH_DATA = "/data/data/com.readboy.scantranslation/lib/";

    public static final String filePath = Environment.getExternalStorageDirectory().toString() + File.separator +
            "Android" + File.separator + "data" + File.separator + "com.readboy.scantranslation" +
            File.separator + "files" + File.separator  + "mounted" + File.separator;

    public static boolean ocrIsInit;

    private static final String TAG = "OcrWorker";
    public void initOcr(Action1<String> action1, Action1<Throwable> errAction){
        Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                //do the init work

                try{
                    ocrIsInit = baseApi.init(filePath,"eng",0);
                    subscriber.onNext("init success");
                    subscriber.onCompleted();
                }catch (IllegalArgumentException exception){
                    subscriber.onError(exception);
                }

//                if (FileUtil.dataIsExists(OCR_PATH_SYSTEM)){
//                    // if system lib has the file ,use system lib to init
//                    Log.d(TAG, "ocr initing");
//                    try {
//                        baseApi.init(OCR_PATH_SYSTEM, "libchi_sim+libpinyin+libeng");
//                        subscriber.onNext("init success");
//                        subscriber.onCompleted();
//                    } catch (IllegalArgumentException e) {
//                        e.printStackTrace();
//                    }
//                }else if (FileUtil.dataIsExists(OCR_PATH_DATA)){
//                    // if own dir has the file ,use own lib to init
//                    baseApi.init(OCR_PATH_DATA, "libchi_sim+libpinyin+libeng");
//                    subscriber.onNext("init success");
//                    subscriber.onCompleted();
//                }else {
//                    // if both libs don't have the file ,throw exception
//                    subscriber.onError(new Exception("没有找到语言包"));
//                }
            }
        }).subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(action1,errAction);
    }

    public void doOcr(final Bitmap bitmap, Action1<String> action1, Action1<Throwable> errAction){
        Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                baseApi.clear();
                baseApi.setImage(bitmap);
                String text;
                long start = System.currentTimeMillis();   //获取开始时间
                text = baseApi.getUTF8Text();
                long end = System.currentTimeMillis(); //获取结束时间
                Log.e(TAG, "getUTF8TextWith() take " + (end - start) + "ms");
                recycleBmp(bitmap);
                if (text != null) {
                    char[] c = text.toCharArray();
                    for (int i = 0; i < c.length - 1; i++) {
                        if ((char) (byte) c[i] != c[i] && c[i + 1] == ' ') {
                            for (int j = i + 1; j < c.length - 1; j++) {
                                c[j] = c[j + 1];
                            }
                            if (c[c.length - 1] != ' ') {
                                c[c.length - 1] = ' ';
                            }
                        }
                    }
                    Log.d(TAG, "do ocr result :" + String.valueOf(c).trim());
                    subscriber.onNext(String.valueOf(c).trim().replace("\n", ""));
                    subscriber.onCompleted();
                } else {
                    subscriber.onError(new Throwable("请对准您要识别的单词"));
                }
            }
        }).subscribeOn(Schedulers.computation())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(action1);
    }

    public void release(){
        if (baseApi != null) {
            baseApi.stop();
        }
    }

    private void recycleBmp(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
