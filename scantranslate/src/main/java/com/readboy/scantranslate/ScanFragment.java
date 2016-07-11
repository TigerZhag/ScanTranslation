package com.readboy.scantranslate;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.readboy.scantranslate.Ocr.OcrWorker;
import com.readboy.scantranslate.Utils.HardwareUtil;
import com.readboy.scantranslate.widght.ScannerOverlayView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import rx.functions.Action1;

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
        initView(view);
        initCamera();
        initOcr();
        return super.onCreateView(inflater, container, savedInstanceState);
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
            camera.autoFocus(focusCallback);
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
                if (success){
                    Log.d(TAG, "onAutoFocus: success");
                    camera.takePicture(null,null,pictureCallback);
                }else {
                    takePicture();
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
                //just ocr
                ScanFragment.this.result = s;
                List<String> result = new ArrayList<>();
                result.add(s);
                scannerView.setResult(result);
                if (ok.getVisibility() != View.VISIBLE) {
                    ok.setVisibility(View.VISIBLE);
                }
                takePicture();
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
