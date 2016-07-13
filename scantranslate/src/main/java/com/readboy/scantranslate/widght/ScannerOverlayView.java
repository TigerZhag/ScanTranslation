package com.readboy.scantranslate.widght;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import com.readboy.scantranslate.R;
import com.readboy.scantranslate.Utils.ImageViewUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author Zhang shixin
 * @date 16-7-7.
 */
public class ScannerOverlayView extends View{

    private Rect frame;
    private DisplayMetrics metrics;

    private Paint paint;

    private String aboveText;
    private List<String> result = new ArrayList<>();

    private static final int textSize = 16;
    private static final int textPaddingTop = 10;

    public ScannerOverlayView(Context context) {
        super(context);
        init();
    }

    public ScannerOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScannerOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        //init the frame
        metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels * 5 / 9;
        int height = width * 2 / 9;
        int leftOffset = (metrics.widthPixels - width) / 2 ;
        int topOffset = (metrics.heightPixels - height) / 4 ;

        frame = new Rect(leftOffset,topOffset,leftOffset + width, topOffset + height);
        paint = new Paint();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // draw the overlay
        /*-
          -------------------------------------
          |                top                |
          -------------------------------------
          |      |                    |       |
          |      |                    |       |
          | left |                    | right |
          |      |                    |       |
          |      |                    |       |
          -------------------------------------
          |              bottom               |
          -------------------------------------
         */
        paint.setColor(getResources().getColor(R.color.scanner_mask));
        canvas.drawRect(0,0,metrics.widthPixels,frame.top,paint);
        canvas.drawRect(0,frame.top,frame.left,frame.bottom,paint);
        canvas.drawRect(frame.right,frame.top,metrics.widthPixels,frame.bottom,paint);
        canvas.drawRect(0,frame.bottom,metrics.widthPixels,metrics.heightPixels,paint);

        //draw the frame

        //draw the text
        paint.setColor(getResources().getColor(R.color.scanner_result));
        paint.setTextSize(textSize * metrics.density);
        if (result.size() > 0){
            paint.setAlpha(0xFF);
            int texttop = (int) (frame.bottom + textPaddingTop * metrics.density);
            Rect rect = new Rect();
            List<String> temp = new ArrayList<>();
            for (String text : result) {
                if (text == null) break;
                int times = (int) (Math.floor(paint.measureText(text,0,text.length()) / getWidth()) + 1);
                if (times == 1) temp.add(text);
                else cutString(text,times,temp);
            }
            for (String text : temp) {
                paint.getTextBounds(text,0,text.length(),rect);
                Typeface phoneticType = Typeface.createFromAsset(getContext().getAssets(),"font/segoeui.ttf");
                paint.setTypeface(phoneticType);
                canvas.drawText(text, (getWidth() - rect.width()) / 2,texttop + rect.height(), paint);
                texttop += rect.height();
            }

        }

        //draw text above the frame
        if (aboveText != null && aboveText.length() > 0){
            Rect rect = new Rect();
            paint.getTextBounds(aboveText, 0, aboveText.length(), rect);
            canvas.drawText(aboveText,frame.left,frame.top - textPaddingTop*metrics.density - rect.height(),paint);
        }
    }

    private void cutString(String text, int times,List<String> list) {
        int minLength = text.length() / times;
        for (int i = 0; i < times; i++) {
            if (i == times - 1){
                list.add(text.substring(minLength * i,text.length()));
            }else {
                list.add(text.substring(minLength * i,minLength * i + minLength));
            }
        }
    }

    public Rect getFrame(){
        return frame;
    }

    public List<String> getResult(){
        return result;
    }

    public void setAboveText(String text){
        this.aboveText = text;
        invalidate();
    }

    public void setResult(List<String> results){
        this.result = results;
        invalidate();
    }

    private static final String TAG = "ScannerOverlayView";

    public Bitmap getScanedImage(Bitmap bitmap) {
        //final Rect displayedImageRect = ImageViewUtil.getBitmapRectCenterInside(bitmap, this);

        // Get the scale factor between the actual Bitmap dimensions and the
        // displayed dimensions for width.
        final float actualImageWidth = bitmap.getWidth();
        final float displayedImageWidth = getWidth();
        final float scaleFactorWidth = actualImageWidth / displayedImageWidth;

        // Get the scale factor between the actual Bitmap dimensions and the
        // displayed dimensions for height.
        final float actualImageHeight = bitmap.getHeight();
        final float displayedImageHeight = getHeight();
        final float scaleFactorHeight = actualImageHeight / displayedImageHeight;

        // Get crop window position relative to the displayed image.
        final float cropWindowX = frame.left;// - displayedImageRect.left;
        final float cropWindowY = frame.top - getTop();
        final float cropWindowWidth = frame.width();
        final float cropWindowHeight = frame.height();

        // Scale the crop window position to the actual size of the Bitmap.
        final float actualCropX = cropWindowX * scaleFactorWidth;
        final float actualCropY = cropWindowY * scaleFactorHeight;
        final float actualCropWidth = cropWindowWidth * scaleFactorWidth;
        final float actualCropHeight = cropWindowHeight * scaleFactorHeight;

        // Crop the subset from the original Bitmap.
        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap,
                (int) actualCropX,
                (int) actualCropY,
                (int) actualCropWidth,
                (int) actualCropHeight);

        return croppedBitmap;
    }
}
