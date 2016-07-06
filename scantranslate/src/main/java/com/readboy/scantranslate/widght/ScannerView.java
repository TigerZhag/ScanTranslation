package com.readboy.scantranslate.widght;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.View;

/**
 * @author Zhang shixin
 * @date 16-7-6.
 */
public class ScannerView extends View {
    public ScannerView(Context context) {
        super(context);
    }

    public ScannerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ScannerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public Bitmap getCroppedImage(Bitmap bmp) {
        return null;
    }
}
