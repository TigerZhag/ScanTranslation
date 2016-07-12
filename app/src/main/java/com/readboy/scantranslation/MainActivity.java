package com.readboy.scantranslation;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.readboy.scantranslate.ScanActivity;
import com.readboy.scantranslate.ScanFragment;

/**
 * @author Zhang shixin
 * @date 16-7-7.
 */
public class MainActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = (Button) findViewById(R.id.start_ocr);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                intent.putExtra(ScanActivity.SCAN_MODE,ScanActivity.OCR_MODE);
                startActivity(intent);
            }
        });

        Button start_translate = (Button) findViewById(R.id.start_translate);
        start_translate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this,ScanActivity.class);
                intent.putExtra(ScanActivity.SCAN_MODE,ScanActivity.TRANSLATE_MODE);
                startActivity(intent);
            }
        });

        Button start_fragment = (Button) findViewById(R.id.start_fragment);
        start_fragment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this,ContainerActivity.class);
//                intent.putExtra(ScanActivity.SCAN_MODE,ScanActivity.TRANSLATE_MODE);
                startActivity(intent);
            }
        });
    }

    private void startFragment() {
        final ScanFragment fragment = new ScanFragment();
        fragment.setOkListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String result = fragment.getScanResult();
                Toast.makeText(MainActivity.this,result,Toast.LENGTH_SHORT).show();
            }
        });
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, fragment).commit();
        Log.d(TAG, "startFragment: ");
    }

    private static final String TAG = "MainActivity";
}

