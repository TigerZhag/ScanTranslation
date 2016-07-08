package com.readboy.scantranslate;

import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.readboy.scantranslate.Ocr.OcrWorker;
import com.readboy.scantranslate.Translation.TranslateResult;
import com.readboy.scantranslate.Translation.Translator;
import com.readboy.scantranslate.Utils.FileUtil;
import com.readboy.scantranslate.Utils.HardwareUtil;
import com.readboy.scantranslate.widght.ScannerOverlayView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
public class ScanActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "ScanActivity";

    private ScannerOverlayView scannerView;
    private FrameLayout previewFrame;
    private TextureView preview;

    private Camera camera;
    private Camera.AutoFocusCallback focusCallback;
    private Camera.PictureCallback pictureCallback;
    private boolean isAutoFocusAble;


    private OcrWorker ocrWorker = new OcrWorker();

    private Button ok;

    public static final String SCAN_MODE = "scan_mode";
    public static final int TRANSLATE_MODE = 0x101;
    public static final int OCR_MODE = 0x102;
    private int MODE = OCR_MODE;

    public void setMode(int mode){
        this.MODE = mode;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        initMode();
        initView();
        initCamera();
        initOcr();
    }

    private void initMode() {
        if (getIntent().getIntExtra(SCAN_MODE,OCR_MODE) == TRANSLATE_MODE){
            //check internet connection
            if (HardwareUtil.isConnect(this)){
                new AlertDialog.Builder(this)
                        .setTitle("网络错误")
                        .setMessage("网络连接失败，请确认网络连接")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                finish();
                            }
                        }).show();
            }else {
                setMode(TRANSLATE_MODE);
            }
        }else {
            setMode(OCR_MODE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //todo init camera
        //openCamera();
        Log.d(TAG, "onResume: ");
        if (preview == null) {
            preview = new TextureView(this);
            preview.setSurfaceTextureListener(this);
            previewFrame.removeAllViews();
            previewFrame.addView(preview);
        }
    }

    private void openCamera() {
        if (camera == null) {
            camera = Camera.open();
            if (camera == null) {
                Toast.makeText(this, R.string.cameraOpenFail, Toast.LENGTH_LONG).show();
                finish();
            }
        }
        Camera.Parameters cameraParameters = camera.getParameters();
        isAutoFocusAble = cameraParameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO);
        if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            cameraParameters.set("orientation", "portrait"); //
            cameraParameters.set("rotation", 90); // 镜头角度转90度（默认摄像头是横拍）
            camera.setDisplayOrientation(90); // 在2.2以上可以使用
        } else { //如果是横屏
            cameraParameters.set("orientation", "landscape"); //
            camera.setDisplayOrientation(0); // 在2.2以上可以使用
        }
        //set the picture format
        //not all devices support arbitrary preview sizes.
//            List<Camera.Size> previewSize = cameraParameters.getSupportedPreviewSizes();
//            List<Camera.Size> pictureSize = cameraParameters.getSupportedPictureSizes();

//            Log.d(TAG, "support size: " + previewSize.size());
//            for (Camera.Size size : previewSize) {
//                Log.d(TAG, "size : width : " + size.width + " height : " + size.height);
//            }
//            for (Camera.Size size : pictureSize) {
//                Log.d(TAG, "support Picture size: width" + size.width + "height : " + size.height);
//            }
//            for (Integer format : cameraParameters.getSupportedPictureFormats()) {
//                Log.d(TAG, "support format:" + format);
//            }
//            Camera.Size maxSize = previewSize.get(previewSize.size() - 1);
        cameraParameters.setPreviewSize(1280, 960);
        cameraParameters.setPictureSize(1280, 960);
        cameraParameters.setPictureFormat(ImageFormat.JPEG);
        camera.setParameters(cameraParameters);
        //exposure compensation: black should minus and white should add in generally;
//            parameters.setExposureCompensation(1);
        camera.setParameters(cameraParameters);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // TODO: 16-7-6  release camera
        releaseCamera();
        Log.d(TAG, "onPause: ");
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            preview = null;
            camera = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TODO: 16-7-6 release Ocr resource
        ocrWorker.release();
    }

    private void initView() {
        scannerView = (ScannerOverlayView) findViewById(R.id.scanner);
        previewFrame = (FrameLayout) findViewById(R.id.scanner_previewframe);
        ok = (Button) findViewById(R.id.scan_ok);

        if (preview == null) {
            preview = new TextureView(this);
            preview.setSurfaceTextureListener(this);
            previewFrame.removeAllViews();
            previewFrame.addView(preview);
        }

        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: 16-7-8 send result to searchActivity
                //Toast.makeText(ScanActivity.this, "send result to searchActivity", Toast.LENGTH_SHORT).show();
                takePicture();
                ok.setVisibility(View.GONE);
                //finish();
            }
        });
    }

    private void initCamera() {
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
                doOcr(scannerView.getScanedImage(bmp));
                camera.startPreview();
            }
        };

        focusCallback = new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (success) {
                    Log.d(TAG, "onAutoFocus: success");
                    doOcr(scannerView.getScanedImage(preview.getBitmap()));
                    //camera.takePicture(null, null, pictureCallback);
                } else {
                    takePicture();
                    Log.e(TAG, "onAutoFocus: failure");
                }
            }
        };
    }

    /**
     * init Ocr
     */
    private void initOcr() {
        Log.d(TAG, "initOcr: start");
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
        ocrWorker.initOcr(action1, errAction);
    }

    /**
     * take a picture and ocr it
     */
    private void takePicture() {
        Log.d(TAG, "takePicture: start aotofocus");
        if (camera != null) {
            camera.autoFocus(focusCallback);
        }
    }

    /**
     * do Ocr
     */
    private void doOcr(Bitmap bitmap) {
        Action1<String> ocrAction = new Action1<String>() {
            @Override
            public void call(String s) {
                Log.d(TAG, "ocr success ,result :" + s);
                //translate(s);
                if (MODE == OCR_MODE){
                    //just ocr
                    List<String> result = new ArrayList<>();
                    result.add(s);
                    scannerView.setResult(result);
                    if (ok.getVisibility() != View.VISIBLE) {
                        ok.setVisibility(View.VISIBLE);
                    }
                    takePicture();
                }else {
                    //auto translate
                    translate(s);
                }
            }
        };
        Action1<Throwable> errAction = new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                Log.e(TAG, "ocr failure");
                List<String> result = new ArrayList<>();
                result.add("识别失败,请对准您要识别的单词");
                scannerView.setResult(result);
                takePicture();
            }
        };
//        String filename = SystemClock.currentThreadTimeMillis() + "ocr.png";
//        FileUtil.saveBitmap(bitmap, filename);
        ocrWorker.doOcr(bitmap, ocrAction, errAction);
    }

    /**
     * do translate and show the result
     *
     * @param query : the word should be translated
     */
    private void translate(String query) {
        Log.d(TAG, "translate: start");
        Observer<TranslateResult> observer = new Observer<TranslateResult>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                takePicture();
                List<String> result = new ArrayList<>();
                result.add("翻译失败,请对准您要识别的单词");
                scannerView.setResult(result);
                Log.d(TAG, "translate onError: " + e.getMessage());
            }

            @Override
            public void onNext(TranslateResult translateResult) {
                Log.d(TAG, "translate success : " + translateResult.source);
                List<String> results = new ArrayList<>();
                results.add(translateResult.source + ":");
                results.add("音标:" + translateResult.phonetic);
                results.addAll(translateResult.mean);
                scannerView.setResult(results);
                takePicture();
            }
        };
        Translator.getInstance().translate(query, observer);
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable: ");
        openCamera();
        try {
            if (camera != null) {
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
//            if (camera != null){
//                camera.stopPreview();
//                camera.release();
//            }
        Log.d(TAG, "onSurfaceTextureDestroyed: ");
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
