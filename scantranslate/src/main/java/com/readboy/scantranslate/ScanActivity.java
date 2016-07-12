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
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.MainThread;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;


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

    private Handler handler;
    private OcrWorker ocrWorker = new OcrWorker();
    public static final String filePath = Environment.getExternalStorageDirectory().toString() + File.separator +
            "Android" + File.separator + "data" + File.separator + "com.readboy.scantranslation" +
            File.separator + "files" + File.separator  + "mounted" + File.separator;


    private Button ok;

    private String result;

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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_scanner);
        handler = new Handler();

        initMode();
        initView();
        initCamera();
        initOcr();
    }

    private void initMode() {
        if (getIntent().getIntExtra(SCAN_MODE,OCR_MODE) == TRANSLATE_MODE){
            //check internet connection
            if (!HardwareUtil.isConnect(this)){
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
        releaseCamera();
        Log.d(TAG, "onPause: ");
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
            preview = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
                //Toast.makeText(ScanActivity.this, "send result to searchActivity", Toast.LENGTH_SHORT).show();
//                takePicture();
                ok.setVisibility(View.GONE);
                // TODO: 16-7-11 handle the ocr result
                finish();
            }
        });
    }

    private void initCamera() {
        //check whether have camera
        if (!HardwareUtil.checkCameraHardware(this)) {
            Toast.makeText(this, R.string.noHardCamera, Toast.LENGTH_SHORT).show();
            finish();
        }

        focusCallback = new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (success) {
                    Log.d(TAG, "onAutoFocus: success");
                    //doOcr(scannerView.getScanedImage(preview.getBitmap()));
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (ScanActivity.this.camera != null) {
                                ScanActivity.this.camera.autoFocus(focusCallback);
                            }
                        }
                    },5000);
                } else {
                    if (camera != null) {
                        camera.autoFocus(focusCallback);
                    }
//                    takePicture();
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
                Log.d(TAG, "ocr inited");
                camera.autoFocus(focusCallback);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (camera != null) {
                            doOcr(scannerView.getScanedImage(preview.getBitmap()));
                        }
                    }
                },1000);
            }
        };
        Action1<Throwable> errAction = new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                // init failure
                Log.e(TAG, "init ocr failure :" + throwable.getMessage());
            }
        };
        if (!isExist()){
            deepFile("tessdata");
        }else {
            ocrWorker.initOcr(action1, errAction);
        }
    }

    public void deepFile(final String path) {
        Log.d(TAG, "deepFile: ");
        Subscriber<String> subscriber = new Subscriber<String>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "onError: init failure");
            }

            @Override
            public void onNext(String s) {
                initOcr();
            }
        };
        Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                try {
                    copyFile(path);
                    subscriber.onNext("init success");
                    subscriber.onCompleted();
                } catch (IOException e) {
                    e.printStackTrace();
                    subscriber.onError(e);
                }
            }
        }).subscribeOn(Schedulers.computation())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(subscriber);


    }

    private void copyFile(String path) throws IOException {
        Log.d(TAG, "copyFile: ");
        String str[] = getAssets().list(path);
        if (str.length > 0) {//如果是目录
            File file = new File(filePath + path);
            file.mkdirs();
            Log.d(TAG, "copyFile: 2");
            for (String string : str) {
                path = path + "/" + string;
                System.out.println("lilongc:\t" + path);
                // textView.setText(textView.getText()+"\t"+path+"\t");
                copyFile(path);
                path = path.substring(0, path.lastIndexOf('/'));
            }
        } else {//如果是文件
            Log.d(TAG, "copyFile: " + path);
            File file = new File(path);
            if(!file.exists()) file.mkdir();
            InputStream is = getAssets().open(path);
            FileOutputStream fos = new FileOutputStream(new File(filePath
                    + path));
            byte[] buffer = new byte[1024];
            int count = 0;
            while (true) {
                count++;
                int len = is.read(buffer);
                if (len == -1) {
                    break;
                }
                fos.write(buffer, 0, len);
            }
            is.close();
            fos.close();
        }
    }

    private boolean isExist() {
        File file = new File(filePath + "tessdata" + File.separator + "eng.traineddata");
        if (file.exists()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * take a picture and ocr it
     */
    private void takePicture() {
        Log.d(TAG, "takePicture: start aotofocus");
        if (camera != null) {
            doOcr(scannerView.getScanedImage(preview.getBitmap()));
        }
    }

    /**
     * do Ocr
     */
    private void doOcr(Bitmap bitmap) {
        Action1<String> ocrAction = new Action1<String>() {
            @Override
            public void call(String s) {
                //translate(s);
                if (MODE == OCR_MODE){
                    //just ocr
                    List<String> result = new ArrayList<>();
                    result.add(s);
                    scannerView.setResult(result);
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
                takePicture();
            }
        };

        Log.d(TAG, "doOcr: start");
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
//                List<String> result = new ArrayList<>();
//                result.add("翻译失败,请对准您要识别的单词");
//                scannerView.setResult(result);
                Log.d(TAG, "translate onError: " + e.getMessage());
            }

            @Override
            public void onNext(TranslateResult translateResult) {
                Log.d(TAG, "translate success : " + translateResult.source);
                result = translateResult.source;
                List<String> results = new ArrayList<>();
                results.add(translateResult.source + ":");
                results.add("音标:" + translateResult.phonetic);
                results.addAll(translateResult.mean);
                scannerView.setResult(results);
                ok.setVisibility(View.VISIBLE);
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
                if (OcrWorker.ocrIsInit){
                    camera.autoFocus(focusCallback);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (camera != null) {
                                doOcr(scannerView.getScanedImage(preview.getBitmap()));
                            }
                        }
                    },1000);
                }
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
