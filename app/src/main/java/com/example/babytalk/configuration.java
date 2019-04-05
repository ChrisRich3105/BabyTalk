package com.example.babytalk;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class configuration extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuration);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
}
