package com.readboy.scantranslate;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
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
        return super.onCreateView(inflater, container, savedInstanceState);
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
                showOK(s);
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
