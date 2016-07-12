package com.readboy.scantranslate.Translation;

import rx.Observer;

/**
 * @author Zhang shixin
 * @date 16-7-5.
 */
public interface Interpreter {
    void translate(String query, Observer<TranslateResult> observer);
}
