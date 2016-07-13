package com.readboy.scantranslate;

import android.content.Intent;
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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
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
import java.util.concurrent.ThreadPoolExecutor;

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
public class ScanFragment extends Fragment implements TextureView.SurfaceTextureListener {
    private static final String TAG = "ScanFragment";
    private static final String OCR_RESULT = "ocr_result";
    /**
     *
     */
    private View.OnClickListener okListener;

    private ScannerOverlayView scannerView;
    private FrameLayout previewFrame;
    private TextureView preview;
    private TextView more;
    private ImageButton back;
    private CheckBox flash;
    private Button start;

    private Camera camera;
    private Camera.AutoFocusCallback focusCallback;
    private boolean isAutoFocusAble;
    private boolean hasFlashLight;

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

        initOcr();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (preview == null){
            preview = new TextureView(getActivity());
            preview.setSurfaceTextureListener(this);
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
            camera = null;
            preview = null;
        }
    }

    private void initOcr() {
        if (!isExist()){
            deepFile("tessdata");
        }else {
            ocrWorker.initOcr(new Action1<String>() {
                @Override
                public void call(String s) {

                }
            }, new Action1<Throwable>() {
                @Override
                public void call(Throwable throwable) {

                }
            });
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
            InputStream is = getActivity().
                    getAssets().open(path);
            FileOutputStream fos = new FileOutputStream(new File(ScanActivity.filePath
                    + path));
            byte[] buffer = new byte[1024];
            while (true) {
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

    private void initView(View view) {
        back = (ImageButton) view.findViewById(R.id.scanner_back);
        flash = (CheckBox) view.findViewById(R.id.scanner_flash);
        scannerView = (ScannerOverlayView) view.findViewById(R.id.scanner);
        previewFrame = (FrameLayout) view.findViewById(R.id.scanner_previewframe);
        more = (TextView) view.findViewById(R.id.scanner_more);
        start = (Button) view.findViewById(R.id.scan_start);

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().finish();
            }
        });

        if (!HardwareUtil.checkFlashLight(getActivity())){
            // has not flashlight
            flash.setVisibility(View.GONE);
        }else {
            flash.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        // flash on
                        HardwareUtil.turnLightOn(camera);
                    } else {
                        // flash off
                        HardwareUtil.turnLightOff(camera);
                    }
                }
            });
        }

        preview = new TextureView(getActivity());
        preview.setSurfaceTextureListener(this);
        previewFrame.removeAllViews();
        previewFrame.addView(preview);

        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: 16-7-13 add the target activity
//                Intent intent = new Intent();
//                intent.putExtra(OCR_RESULT,result);
//                startActivity(intent);
                getActivity().finish();
            }
        });

        start.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN){
                    //press the button
                    Log.d(TAG, "onGenericMotion: press");
                    ocrrrrStop = false;
                    too_temp = true;

                    start.setText(getString(R.string.scanner_lock));
                    more.setVisibility(View.GONE);
                    startOcr();
                }else if (event.getAction() == MotionEvent.ACTION_UP){
                    // release the button
                    Log.d(TAG, "onGenericMotion: release");
                    stopOcr();
                    start.setText(getString(R.string.scanner_start));
                    if (too_temp){
                        Toast.makeText(getActivity(), R.string.press_toosoon, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    if (result == null) return true;

                    scannerView.setAboveText(getString(R.string.lock_word));
                    more.setVisibility(View.VISIBLE);
                }
                return true;
            }
        });
    }

    private boolean too_temp;

    private boolean ocrrrrStop ;
    private void stopOcr() {
        ocrrrrStop = true;
    }

    private void startOcr() {
        if (ocrrrrStop) return;
        Action1<String> action = new Action1<String>() {
            @Override
            public void call(String s) {
                if (!ocrrrrStop){
                    // TODO: 16-7-13 hide the middle result
                    scannerView.setAboveText(s);
                    translate(s);
                    //continue
                    //startOcr();
                }
            }
        };

        Action1<Throwable> errAction = new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                //ocr failure
                if (camera == null) return;
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success) startOcr();
                        else {
                            Toast.makeText(getActivity(), R.string.ocr_failure, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        };
        ocrWorker.doOcr(bitmap2Gray(scannerView.getScanedImage(preview.getBitmap())),action,errAction);
    }

    private void translate(String s) {
        if (ocrrrrStop) return;
        Translator.getInstance().translate(s, new Subscriber<TranslateResult>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                //request camera auto focus to take picture again
                if (camera == null) return;
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success) startOcr();
                        else {
                            Toast.makeText(getActivity(), R.string.focus_failure, Toast.LENGTH_SHORT).show();
                            startOcr();
                        }
                    }
                });
            }

            @Override
            public void onNext(TranslateResult translateResult) {
                if (ocrrrrStop) return;
                too_temp = false;

                result = translateResult.source;
                List<String> results = new ArrayList<>();
                results.add(translateResult.source);
                results.add(translateResult.phonetic);
                results.addAll(translateResult.mean);
                scannerView.setResult(results);
                more.setVisibility(View.VISIBLE);
                // continue ocr
                startOcr();
            }
        });
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

    public Bitmap bitmap2Gray(Bitmap bmSrc) {
        // 得到图片的长和宽
        int width = bmSrc.getWidth();
        int height = bmSrc.getHeight();
        // 创建目标灰度图像
        Bitmap bmpGray = null;
        bmpGray = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        // 创建画布
        Canvas c = new Canvas(bmpGray);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmSrc, 0, 0, paint);
        return bmpGray;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera();
        try {
            camera.setPreviewTexture(surface);
            camera.startPreview();
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {

                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
