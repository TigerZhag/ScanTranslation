package com.readboy.scantranslation;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.readboy.scantranslate.ScanActivity;

/**
 * @author Zhang shixin
 * @date 16-7-7.
 */
public class MainActivity extends Activity {
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
    }
}
