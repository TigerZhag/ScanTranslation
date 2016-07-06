package com.readboy.scantranslate.Translation.youdao;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * @author Zhang shixin
 * @date 16-7-6.
 */
public class YoudaoTranslateJsonBean {
    /**
     *  {"translation":["好"],"basic":{"us-phonetic":"ɡʊd","phonetic":"gʊd","uk-phonetic":"gʊd","explains":["n. 好处；善行；慷慨的行为","adj. 好的；优良的；愉快的；虔诚的","adv. 好","n. (Good)人名；(英)古德；(瑞典)戈德"]},"query":"good","errorCode":0,"web":[{"value":["好","良好","商品"],"key":"Good"},{"value":["晚安","晚上好","曾轶可"],"key":"good night"},{"value":["很好","相当好","非常好"],"key":"very good"}]}
     *
     *  translation : 好
     *  basic ; {"us-phonetic":"ɡʊd","phonetic":"gʊd","uk-phonetic":"gʊd","explains":["n. 好处；善行；慷慨的行为","adj. 好的；优良的；愉快的；虔诚的","adv. 好","n. (Good)人名；(英)古德；(瑞典)戈德"]}
     *  query : good
     *  errorCode : 0
     *  web : [{"value":["好","良好","商品"],"key":"Good"},{"value":["晚安","晚上好","曾轶可"],"key":"good night"},{"value":["很好","相当好","非常好"],"key":"very good"}]
     *
     */
    private List<String> translation;
    private MyBasic basic;
    private String query;
    private String errorCode;
    private List<MyWeb> web;

    public static class MyBasic {
        /**
         *  us-phonetic : ɡʊd
         *  phonetic : gʊd
         *  uk-phonetic : gʊd
         *  explains : ["n. 好处；善行；慷慨的行为","adj. 好的；优良的；愉快的；虔诚的","adv. 好","n. (Good)人名；(英)古德；(瑞典)戈德"]
         */
        @SerializedName("us-phonetic")
        private String us_phonetic;
        private String phonetic;
        @SerializedName("uk-phonetic")
        private String uk_phonetic;
        private List<String> explains;

        public String getUs_phonetic() {    return us_phonetic; }

        public void setUs_phonetic(String us_phonetic) {    this.us_phonetic = us_phonetic; }

        public String getPhonetic() {   return phonetic;    }

        public void setPhonetic(String phonetic) {  this.phonetic = phonetic;   }

        public String getUk_phonetic() {    return uk_phonetic; }

        public void setUk_phonetic(String uk_phonetic) {    this.uk_phonetic = uk_phonetic; }

        public List<String> getExplains() { return explains;    }

        public void setExplains(List<String> explains) {    this.explains = explains;   }

    }

    public static class MyWeb {
        /**
         *  value : ["好","良好","商品"]
         *  key : Good
         */
        private List<String> value;
        private String key;

        public List<String> getValue() {   return value;   }

        public void setValue(List<String> value) { this.value = value; }

        public String getKey() {    return key; }

        public void setKey(String key) {    this.key = key; }

    }

    public String getQuery() {  return query;   }

    public void setQuery(String query) {    this.query = query; }

    public List<String> getTranslation() {    return translation; }

    public void setTranslation(List<String> translation) {    this.translation = translation; }

    public MyBasic getBasic() { return basic;   }

    public void setBasic(MyBasic basic) {   this.basic = basic; }

    public String getErrorCode() {  return errorCode;   }

    public void setErrorCode(String errorCode) {    this.errorCode = errorCode; }

    public List<MyWeb> getWeb() {   return web; }

    public void setWeb(List<MyWeb> web) {   this.web = web; }
}
