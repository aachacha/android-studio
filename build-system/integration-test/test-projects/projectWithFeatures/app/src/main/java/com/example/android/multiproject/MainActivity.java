package com.example.android.multiproject;

import android.app.Activity;
import android.os.Bundle;
import com.example.android.multiproject.app.R;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
}
