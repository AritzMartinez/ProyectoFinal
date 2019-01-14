package com.example.msimm.fitegibide;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        titulo_app = findViewById(R.id.tituloapp);
        titulo_app.setTextColor(Color.parseColor("#FE642E"));
        titulo_app.setTextSize(35);
    }

    TextView titulo_app;



}
