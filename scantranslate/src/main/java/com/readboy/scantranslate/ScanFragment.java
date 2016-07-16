package com.readboy.scantranslate;

import android.content.res.Configuration;
import android.graphics.Bitmap;
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

import com.readboy.library.quicksearch.QuickSearchWord;
import com.readboy.scantranslate.Ocr.OcrWorker;
import com.readboy.scantranslate.Translation.TranslateResult;
import com.readboy.scantranslate.Translation.Translator;
import com.readboy.scantranslate.Utils.HardwareUtil;
import com.readboy.scantranslate.widght.ScannerOverlayView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import rx.Observable;
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
//        initTranslate();

        return view;
    }

    private QuickSearchWord quickSearchWord;

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
//        quickSearchWord.destroy();
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
        String str[] = getActivity().getAssets().list(path);
        if (str.length > 0) {//如果是目录
            File file = new File(ScanActivity.filePath + path);
            file.mkdirs();
            for (String string : str) {
                path = path + "/" + string;
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
                    if(too_temp) {
                        Toast.makeText(getActivity(), R.string.press_toosoon, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    ocrrrrStop = false;
                    too_temp = true;
                    scannerView.setAboveText(getString(R.string.scanner_ing));
                    start.setText(getString(R.string.scanner_lock));
                    more.setVisibility(View.GONE);
                    if (translate_finish) {
                        startOcr();
                    }
                }else if (event.getAction() == MotionEvent.ACTION_UP){
                    // release the button
                    Log.d(TAG, "onGenericMotion: release");
                    stopOcr();
                    start.setText(getString(R.string.scanner_start));
                    if (too_temp){
                        Toast.makeText(getActivity(), R.string.press_toosoon, Toast.LENGTH_SHORT).show();
                        scannerView.setAboveText("");
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

    private Action1<String> ocrAction = new Action1<String>() {
        @Override
        public void call(String s) {
            too_temp = false;
            if (!ocrrrrStop){
                // TODO: 16-7-13 hide the middle result
                scannerView.setAboveText(s);
                translate(s.toLowerCase().replaceAll("[^(A-Za-z)]", ""));
                //continue
//                startOcr();
            }
        }
    };
    private Action1<Throwable> ocrErrAction = new Action1<Throwable>() {
        @Override
        public void call(Throwable throwable) {
            //ocr failure

        }
    };
    private void startOcr() {
        if (ocrrrrStop) return;
        ocrWorker.doOcr(bitmap2Gray(scannerView.getScanedImage(preview.getBitmap())), ocrAction, ocrErrAction);
    }

    private Subscriber<TranslateResult> translateResultSubscriber = new Subscriber<TranslateResult>() {
        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            //request camera auto focus to take picture again

        }

        @Override
        public void onNext(TranslateResult translateResult) {
            if (ocrrrrStop) return;

            result = translateResult.source;
            List<String> results = new ArrayList<>();
            results.add(translateResult.source);
            results.add(translateResult.phonetic);
            results.addAll(translateResult.mean);
            scannerView.setResult(results);
            more.setVisibility(View.VISIBLE);
//                more.setTypeface(Typeface.createFromAsset(getContext().getAssets(), "font/segoeui.ttf"));
//                Log.d(TAG, "onNext: phonetic: " + translateResult.phonetic);
//                more.setText(translateResult.phonetic);
            // continue ocr
            startOcr();
        }
    };

    private boolean translate_finish = true ;
    private long translate_begin;
    private void initTranslate() {
        quickSearchWord = new QuickSearchWord(getContext());
        quickSearchWord.setMaxCount(100);
        quickSearchWord.setListener(new QuickSearchWord.OnQuickSearchListener() {
            @Override
            public void onResult(int status, String result) {
                translate_finish = true;
                if (ocrrrrStop) return;
                Log.e(TAG, "translate take time " + (SystemClock.currentThreadTimeMillis() - translate_begin) + "ms" );
                if (status == quickSearchWord.STATUS_SUCCESS){
                    Log.d(TAG, "translate result :" + result);
                    String[] temp = result.split("\n");
                    List<String> results = new ArrayList<>(temp.length);
                    for (int i = 0; i < temp.length;i ++) {
//                        if (i == 8) break;
//                        if (temp[i].length() > 20) temp[i] = temp[i].substring(0,20);
                        results.add(temp[i]);
                    }
                    scannerView.setResult(results);
                    more.setVisibility(View.VISIBLE);
                }
                startOcr();
            }
        });
    }

    private void translate(final String s) {
        if (ocrrrrStop) return;
        translate_begin = SystemClock.currentThreadTimeMillis();
        Log.e(TAG, "translate: " + s);
        translate_finish = false;
//        quickSearchWord.search(s);
        Translator.getInstance().translate(s, translateResultSubscriber);
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
        if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            cameraParameters.set("orientation", "portrait"); //
            cameraParameters.set("rotation", 90); // 镜头角度转90度（默认摄像头是横拍）
            camera.setDisplayOrientation(90); // 在2.2以上可以使用
        } else { //如果是横屏
            cameraParameters.set("orientation", "landscape"); //
            camera.setDisplayOrientation(0); // 在2.2以上可以使用
        }
//        cameraParameters.setPreviewSize(1280, 960);
        cameraParameters.setPictureFormat(ImageFormat.JPEG);
        cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
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
