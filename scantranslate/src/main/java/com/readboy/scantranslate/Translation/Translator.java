package com.readboy.scantranslate.Translation;

import com.readboy.scantranslate.Translation.Baidu.BaiduInterpreter;
import com.readboy.scantranslate.Translation.youdao.YoudaoInterpreter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rx.Observer;

/**
 * @author Zhang shixin
 * @date 16-7-5.
 */
public class Translator {
    private static Translator translator = new Translator();

    /**
     * Interpreter does the translate work in acctually
     * use Youdao to translate by default
     */
    private Interpreter interpreter;

    public static final int BAIDU_INTERPRETER = 0x001;
    public static final int YOUDAO_INTERPRETER = 0x002;
    public static final int DEFAULT_INTERPRETER = YOUDAO_INTERPRETER;

    /**
     * the Thread pool to execute the translate work
     * use this could manage the translate event conveniently
     */
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private Translator(){

    }

    public static Translator getInstance(){
        if (translator == null){
            translator = new Translator();
        }
        return translator;
    }

    public void setInterpreter(int type){
        switch (type){
            case BAIDU_INTERPRETER:
                interpreter = new BaiduInterpreter();
                break;
            case YOUDAO_INTERPRETER:
            default:
                interpreter = new YoudaoInterpreter();
        }
    }

    public void setInterpreter(Interpreter interpreter){
        this.interpreter = interpreter;
    }

    public void translate(String query, Observer<TranslateResult> observer){
        if (interpreter == null){
            setInterpreter(DEFAULT_INTERPRETER);
        }
        interpreter.translate(query,observer);
    }
}
