package com.readboy.scantranslate.Translation.youdao;

import android.util.Log;

import com.readboy.scantranslate.Translation.InterpretService;
import com.readboy.scantranslate.Translation.Interpreter;
import com.readboy.scantranslate.Translation.TranslateResult;

import java.util.concurrent.TimeUnit;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

/**
 * @author Zhang shixin
 * @date 16-7-5.
 *
 * 有道牌翻译器
 */
public class YoudaoInterpreter implements Interpreter {
    private static final String TAG = "YouDaoTranslation";
    private static final String UTF8 = "utf-8";
    private static final String API_KEY = "1080407158"; //用户的appid
    private static final String KEY_FROM = "readboy";   //用户密钥
    private static final String URL = "http://fanyi.youdao.com/"; //翻译地址
    private static final String TYPE = "data";
    private static final String DOCTYPE = "json";
    private static final String VERSION = "1.1";

    private InterpretService service;

    @Override
    public void translate(String query, Observer<TranslateResult> observer) {
        if (service == null){
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                    .build();
            service = retrofit.create(InterpretService.class);
        }
        service.getYoudaoTranslateJsonBean(KEY_FROM,API_KEY,TYPE,DOCTYPE,VERSION,query)
                .throttleLast(500, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .map(new Transformer())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(observer);
    }

    private class Transformer implements Func1<YoudaoTranslateJsonBean, TranslateResult> {

        @Override
        public TranslateResult call(YoudaoTranslateJsonBean bean) {
            TranslateResult result = new TranslateResult();
            result.source = bean.getQuery();
            result.phonetic = bean.getBasic().getPhonetic();
            result.mean = bean.getBasic().getExplains();
            return result;
        }
    }
}
