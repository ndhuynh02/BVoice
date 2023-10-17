package com.example.bvoice;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

public class MainActivity extends AppCompatActivity {
    private AppCompatButton translateBtn;
    private AppCompatButton studyBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        translateBtn = findViewById(R.id.translate_button);
        studyBtn = findViewById(R.id.study_button);

        translateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                translateBtn.setBackgroundResource(R.drawable.button_gradient);
                translateBtn.setTextColor(getColor(R.color.white));
                openCamera();
            }
        });

        translateBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    // Finger touched the button (pressed)
                    translateBtn.setBackgroundResource(R.drawable.button_gradient);
                    translateBtn.setTextColor(getColor(R.color.white));
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    // Finger lifted from the button
                    translateBtn.setBackgroundResource(R.drawable.button_gradient_border);
                    translateBtn.setTextColor(getColor(R.color.black)); // Return to default
                }
                return false; // Continue processing the event
            }
        });

        studyBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    // Finger touched the button (pressed)
                    studyBtn.setBackgroundResource(R.drawable.button_gradient);
                    studyBtn.setTextColor(getColor(R.color.white));
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    // Finger lifted from the button
                    studyBtn.setBackgroundResource(R.drawable.button_gradient_border);
                    studyBtn.setTextColor(getColor(R.color.black)); // Return to default
                }
                return false; // Continue processing the event
            }
        });
    }

    private void openCamera() {
        Intent intent = new Intent(this, TranslateActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}