package com.readboy.scantranslate;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.readboy.scantranslate.Ocr.OcrWorker;
import com.readboy.scantranslate.Translation.TranslateResult;
import com.readboy.scantranslate.Translation.Translator;
import com.readboy.scantranslate.Utils.HardwareUtil;
import com.readboy.scantranslate.widght.ScannerView;

import java.io.IOException;

import rx.Observer;
import rx.functions.Action1;


/**
 * Event sequence:
 *
 * @author Zhang shixin
 * @date 16-7-6.
 *
 *   -----------                ----------
 *  |Main Thread|              |I/O Thread|
 *   -----------                ----------
 *       |                          |
 *   init camera                 init Ocr
 *       |                          |
 *       |                          |
 *  start preview                   |
 *       |                          |
 *  ->take picture <----------------
 * |     |                          |
 * |     |                          |
 * |      -------------------->    Ocr
 * |  preview                       |
 * |     |                          |
 * |     |                      translate
 * | show result   <----------------
 * |     |                          |
 * |_____|                          |
 *
 */
public class ScanActivity extends AppCompatActivity {
    private static final String TAG = "ScanActivity";

    private ScannerView scannerView;
    private FrameLayout previewFrame;
    private TextureView preview;

    private Camera camera;
    private Camera.AutoFocusCallback focusCallback;
    private Camera.PictureCallback pictureCallback;
    private boolean isAutoFocusAble;


    private OcrWorker ocrWorker = new OcrWorker();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);
        initView();
        initOcr();
        initCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //todo init camera
        openCamera();
    }

    private void openCamera() {
        if (preview == null){
            preview = new TextureView(this);
            preview.setSurfaceTextureListener(new MySurfaceTextureListener());
            previewFrame.removeAllViews();
            previewFrame.addView(preview);
        }
        if (camera == null) {
            camera = Camera.open();
        }
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            isAutoFocusAble = parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO);

            if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                parameters.set("orientation", "portrait"); //
                parameters.set("rotation", 90); // 镜头角度转90度（默认摄像头是横拍）
                camera.setDisplayOrientation(90); // 在2.2以上可以使用
            } else{ //如果是横屏
                parameters.set("orientation", "landscape"); //
                camera.setDisplayOrientation(0); // 在2.2以上可以使用
            }
            parameters.setExposureCompensation(1);
            camera.setParameters(parameters);

        } else {
            Toast.makeText(this, R.string.cameraOpenFail, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        camera.startPreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // TODO: 16-7-6  release camera
        releaseCamera();
    }

    private void releaseCamera() {
        if (camera != null){
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TODO: 16-7-6 release Ocr resource
    }

    private void initView(){
        scannerView = (ScannerView) findViewById(R.id.scanner);
        previewFrame = (FrameLayout) findViewById(R.id.scanner_previewframe);
    }

    private void initCamera(){
        //check whether have camera
        if (!HardwareUtil.checkCameraHardware(this)) {
            Toast.makeText(this, R.string.noHardCamera, Toast.LENGTH_SHORT).show();
            finish();
        }

        //set camera paramter

        pictureCallback = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                //拍照成功,裁剪
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                bmOptions.inPurgeable = true;
                bmOptions.inInputShareable = true;
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, bmOptions);
                doOcr(scannerView.getCroppedImage(bmp));
                camera.startPreview();
            }
        };

        focusCallback = new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (success){
                    camera.takePicture(null,null,pictureCallback);
                }else {
                    Log.e(TAG, "onAutoFocus: failure");
                }
            }
        };
    }

    /**
     * init Ocr
     */
    private void initOcr(){
        Action1<String> action1 = new Action1<String>() {
            @Override
            public void call(String s) {
                // start autoFocus to take picture
                takePicture();
            }
        };
        Action1<Throwable> errAction = new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                // init failure
                Log.e(TAG, "init ocr failure :" + throwable.getMessage());
            }
        };
        ocrWorker.initOcr(action1,errAction);
    }

    /**
     * take a picture and ocr it
     */
    private void takePicture() {
        camera.autoFocus(focusCallback);
    }

    /**
     * do Ocr
     */
    private void doOcr(Bitmap bitmap){
        Action1<String> ocrAction = new Action1<String>() {
            @Override
            public void call(String s) {
                Log.d(TAG, "Ocr success ,result :" + s);
                translate(s);
            }
        };
        ocrWorker.doOcr(bitmap,ocrAction);
    }

    /**
     * do translate and show the result
     * @param query : the word should be translated
     */
    private void translate(String query){
        Observer<TranslateResult> observer = new Observer<TranslateResult>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                Log.d(TAG, "translate onError: " + e.getMessage());
            }

            @Override
            public void onNext(TranslateResult translateResult) {
                StringBuilder builder = new StringBuilder();
                builder.append(translateResult.source).append("\n")
                        .append(translateResult.phonetic).append("\n");
                for (String mean : translateResult.mean) {
                    builder.append(mean).append("\n");
                }
            }
        };
        Translator.getInstance().translate(query,observer);
    }

    private class MySurfaceTextureListener implements TextureView.SurfaceTextureListener{

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            try {
                if (camera != null){
                    camera.setPreviewTexture(surface);
                    camera.startPreview();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (camera != null){
                camera.stopPreview();
                camera.release();
            }
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    }
}
