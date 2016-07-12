package com.readboy.scantranslate;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.readboy.scantranslate.Ocr.OcrWorker;
import com.readboy.scantranslate.Translation.TranslateResult;
import com.readboy.scantranslate.Translation.Translator;
import com.readboy.scantranslate.Utils.FileUtil;
import com.readboy.scantranslate.Utils.HardwareUtil;
import com.readboy.scantranslate.widght.ScannerOverlayView;

import java.io.File;
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
 * @author Zhang shixin
 * @date 16-7-8.
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
 * |     |                          |
 * |_____|                          |
 *
 *
 */
public class ScanFragment extends Fragment {
    private static final String TAG = "ScanFragment";
    /**
     *
     */
    private View.OnClickListener okListener;

    private ScannerOverlayView scannerView;
    private FrameLayout previewFrame;
    private TextureView preview;
    private Button ok;

    private Camera camera;
    private Camera.AutoFocusCallback focusCallback;
    private Camera.PictureCallback pictureCallback;
    private boolean isAutoFocusAble;

    private String result;
    private OcrWorker ocrWorker = new OcrWorker();

    private Handler handler;

    public void setOkListener(View.OnClickListener listener){
        this.okListener = listener;
    }


    public String getScanResult(){
        return result;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.activity_scanner,null,false);
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler = new Handler();
        initView(view);
        initCamera();
        initOcr();
        return view;
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
                camera.autoFocus(focusCallback);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (camera != null) {
                            doOcr(getGrayBitmap(scannerView.getScanedImage(preview.getBitmap())));
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

    public Bitmap getGrayBitmap(Bitmap mBitmap) {
        Bitmap mGrayBitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas mCanvas = new Canvas(mGrayBitmap);
        Paint mPaint = new Paint();

        //创建颜色变换矩阵
        ColorMatrix mColorMatrix = new ColorMatrix();
        //设置灰度影响范围
        mColorMatrix.setSaturation(0);
        //创建颜色过滤矩阵
        ColorMatrixColorFilter mColorFilter = new ColorMatrixColorFilter(mColorMatrix);
        //设置画笔的颜色过滤矩阵
        mPaint.setColorFilter(mColorFilter);
        //使用处理后的画笔绘制图像
        mCanvas.drawBitmap(mBitmap, 0, 0, mPaint);
//        FileUtil.saveBitmap(mGrayBitmap, SystemClock.currentThreadTimeMillis() + "ocr.jpg");
        return mGrayBitmap;
    }

    private void deepFile(final String path) {
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
        String str[] = getActivity().getAssets().list(path);
        if (str.length > 0) {//如果是目录
            File file = new File(ScanActivity.filePath + path);
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
            InputStream is = getActivity().getAssets().open(path);
            FileOutputStream fos = new FileOutputStream(new File(ScanActivity.filePath
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
        File file = new File(ScanActivity.filePath + "tessdata" + File.separator + "eng.traineddata");
        if (file.exists()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        //start preview
        Log.d(TAG, "onResume: ");
        if (preview == null) {
            preview = new TextureView(getActivity());
            preview.setSurfaceTextureListener(new MySurfaceTextureListener());
            previewFrame.removeAllViews();
            previewFrame.addView(preview);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ocrWorker.release();
    }


    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            preview = null;
            camera = null;
        }
    }

    /**
     * take a picture and ocr it
     */
    private void takePicture() {
        Log.d(TAG, "takePicture: start aotofocus");
        if (camera != null){
            //camera.autoFocus(focusCallback);
            doOcr(getGrayBitmap(scannerView.getScanedImage(preview.getBitmap())));
        }
    }

    private void initView(View view) {
        scannerView = (ScannerOverlayView) view.findViewById(R.id.scanner);
        previewFrame = (FrameLayout) view.findViewById(R.id.scanner_previewframe);
        ok = (Button) view.findViewById(R.id.scan_ok);

        if (preview == null){
            preview = new TextureView(getActivity());
            preview.setSurfaceTextureListener(new MySurfaceTextureListener());
            previewFrame.removeAllViews();
            previewFrame.addView(preview);
        }

        scannerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camera != null){
                    camera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {

                        }
                    });
                }
            }
        });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                okListener.onClick(v);
            }
        });
    }

    private void initCamera(){
        //check whether have camera
        if (!HardwareUtil.checkCameraHardware(getActivity())) {
            Toast.makeText(getActivity(), R.string.noHardCamera, Toast.LENGTH_SHORT).show();
            getActivity().finish();
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
                            if (ScanFragment.this.camera != null) {
                                ScanFragment.this.camera.autoFocus(focusCallback);
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
     * do Ocr
     */
    private void doOcr(Bitmap bitmap){
        Action1<String> ocrAction = new Action1<String>() {
            @Override
            public void call(String s) {
                Log.d(TAG, "ocr success ,result :" + s);
                translate(s.toLowerCase());
            }
        };
        Action1<Throwable> errAction = new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                Log.e(TAG, "ocr failure");
                takePicture();
            }
        };
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

    private void showOK(String result) {
        this.result = result;
        ok.setVisibility(View.VISIBLE);
    }

    private class MySurfaceTextureListener implements TextureView.SurfaceTextureListener{

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
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

    private void openCamera() {
        if (camera == null) {
            camera = Camera.open();
            if (camera == null) {
                Toast.makeText(getActivity(), R.string.cameraOpenFail, Toast.LENGTH_LONG).show();
                getActivity().finish();
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
}
