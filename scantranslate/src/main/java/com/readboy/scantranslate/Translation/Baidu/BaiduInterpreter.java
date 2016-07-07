package com.readboy.scantranslate.Translation.Baidu;

import com.readboy.scantranslate.Translation.InterpretService;
import com.readboy.scantranslate.Translation.Interpreter;
import com.readboy.scantranslate.Translation.TranslateResult;

import java.util.ArrayList;
import java.util.Random;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * @author Zhang shixin
 * @date 16-7-5.
 *
 */
public class BaiduInterpreter implements Interpreter {

    private static final String TAG = "BaiDuTranslation";
    private static final String UTF8 = "utf-8";
    public static final String BAIDU_URL = "http://api.fanyi.baidu.com/api/trans/vip/";
    public static final String APP_ID = "20160701000024342";
    public static final String KEY = "mI7xnJSryaY8NHyeTSlB";
    private Random random = new Random();
    private String md5EncryptSign;  //md5加密后的签名
    private int salt;   //随机数
    private static String fromLanguage = "en";
    private String targetLanguage = "zh";

    private InterpretService service;

    @Override
    public void translate(String query, Observer<TranslateResult> observer) {
        if (service == null){
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BAIDU_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            service = retrofit.create(InterpretService.class);
        }
        service.getBaiduTranslateJsonBean(query,fromLanguage,targetLanguage,APP_ID,salt,md5EncryptSign)
                .subscribeOn(Schedulers.io())
                .map(new Transformer())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(observer);
    }

    private class Transformer implements Func1<BaiduTranslateJsonBean,TranslateResult>{

        @Override
        public TranslateResult call(BaiduTranslateJsonBean bean) {
            TranslateResult result = new TranslateResult();
            if (bean == null) return null;
            if (bean.getTrans_result() == null) return null;
            if (bean.getTrans_result().size() == 0 ) return null;
            result.source = bean.getTrans_result().get(0).getSrc();
            result.phonetic = "";
            result.mean = new ArrayList<>();
            result.mean.add(bean.getTrans_result().get(0).getDst());
            return result;
        }
    }
}
