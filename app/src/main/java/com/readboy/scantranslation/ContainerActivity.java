package com.readboy.scantranslation;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.view.View;

import com.readboy.scantranslate.ScanFragment;

/**
 * @author Zhang shixin
 * @date 16-7-11.
 */
public class ContainerActivity extends FragmentActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container);

        ScanFragment fragment = new ScanFragment();
        fragment.setOkListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        getSupportFragmentManager().beginTransaction()
                .add(R.id.frame_container,fragment).commit();
    }
}
