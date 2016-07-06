package com.readboy.scantranslate.Translation.Baidu;

import java.util.List;

/**
 * @author Zhang shixin
 * @date 16-7-6.
 */
public class BaiduTranslateJsonBean {
    /**
     *  {from":"en","to":"zh","trans_result":[{"src":"apple","dst":"\u82f9\u679c"}]}
     *
     *  from : en
     *  to : zh
     *  trans_result : [{"src":"apple","dst":"\u82f9\u679c"}] 数组
     */

    private String from;
    private String to;
    private List<TransResult> trans_result;

    public List<TransResult> getTrans_result() {
        return trans_result;
    }

    public void setTrans_result(List<TransResult> trans_result) {
        this.trans_result = trans_result;
    }

    public String getTo() { return to;  }

    public void setTo(String to) {  this.to = to;   }

    public String getFrom() {   return from;    }

    public void setFrom(String from) {  this.from = from;   }

    public static class TransResult{

        /**
         *  src : apple
         *  dst : \u82f9\u679c
         */
        private String src;
        private String dst;

        public String getSrc() {    return src; }

        public void setSrc(String src) {    this.src = src; }

        public String getDst() {    return dst; }

        public void setDst(String dst) {    this.dst = dst; }

    }

    /**
     *  {"error_code":"52003","error_msg":"UNAUTHORIZED USER"}
     *  error_code : 52003
     *  error_msg : UNAUTHORIZED USER
     */

    private String error_code;
    private String error_msg;

    public String getError_code() {
        return error_code;
    }

    public void setError_code(String error_code) {
        this.error_code = error_code;
    }

    public String getError_msg() {
        return error_msg;
    }

    public void setError_msg(String error_msg) {
        this.error_msg = error_msg;
    }

}
