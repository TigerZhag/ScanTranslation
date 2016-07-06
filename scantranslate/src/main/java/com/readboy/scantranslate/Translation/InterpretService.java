package com.readboy.scantranslate.Translation;

import com.readboy.scantranslate.Translation.Baidu.BaiduTranslateJsonBean;
import com.readboy.scantranslate.Translation.youdao.YoudaoTranslateJsonBean;

import retrofit2.http.GET;
import retrofit2.http.Query;
import rx.Observable;

/**
 * @author Zhang shixin
 * @date 16-7-6.
 */
public interface InterpretService {
    /**
     * Baidu Api
     *
     */
    @GET("translate")
    Observable<BaiduTranslateJsonBean> getBaiduTranslateJsonBean(@Query("q") String q, @Query("from") String from, @Query("to") String to
            , @Query("appid") String appid, @Query("salt") int salt, @Query("sign") String sign);

    /**
     * Youdao Api
     */
    @GET("openapi.do")
    Observable<YoudaoTranslateJsonBean> getYoudaoTranslateJsonBean(@Query("keyfrom") String keyfrom, @Query("key") String key,
                                                                   @Query("type") String type, @Query("doctype") String doctype,
                                                                   @Query("version") String version, @Query("q") String q);
}
