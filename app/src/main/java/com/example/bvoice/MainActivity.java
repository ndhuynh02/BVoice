package com.example.bvoice;

import static com.example.bvoice.ModelClass.generateRandomArray;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

//import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private AppCompatButton translateBtn;
    private AppCompatButton studyBtn;

    private ModelClass model;



    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try{
//            Interpreter.Options options = new Interpreter.Options();

            model = new ModelClass(getAssets(),"model.tflite","app/src/main/assets/sign_to_prediction_index_map.json");
            System.out.println("HIIIIIIIIIIIIIIIIIIIIIi");
            Log.d("MainActivity","Model is successfully loaded");
        } catch (IOException e) {
            Log.d("MainActivity", "Getting some error");
            e.printStackTrace();
        }
        float[][][] inputArray = generateRandomArray(25,543,3);
//        float[] output = new float[250];
//        model.interpreter.run(inputArray,output);
        String prediction = new String();
        prediction = model.predict(inputArray);
//        System.out.println(output);
        Log.d("NHUTHAO_mainactivity",  prediction);
        translateBtn = findViewById(R.id.translate_button);
        studyBtn = findViewById(R.id.study_button);

        translateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                translateBtn.setBackgroundResource(R.drawable.button_gradient);
//                translateBtn.setTextColor(getColor(R.color.white));
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

        studyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                studyBtn.setBackgroundResource(R.drawable.button_gradient);
//                studyBtn.setTextColor(getColor(R.color.white));
                openStudy();
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

    private void openStudy() {
        Intent intent = new Intent(this, StudyActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}