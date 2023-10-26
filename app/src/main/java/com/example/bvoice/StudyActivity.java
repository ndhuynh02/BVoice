package com.example.bvoice;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StudyActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private ImageButton gobackBtn;
    private ListView vocabList;

    String [] vocabulary;
    int intCount = 0;

    InputStream inputStreamCounter;
    BufferedReader bufferedReaderCounter;

    InputStream inputStreamLoader;
    BufferedReader bufferedReaderLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        gobackBtn = findViewById(R.id.go_back_btn);
        gobackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backToMenu();
            }
        });

        // counter
        inputStreamCounter = this.getResources().openRawResource(R.raw.vocab_list);
        bufferedReaderCounter = new BufferedReader(new InputStreamReader(inputStreamCounter));

        // loader
        inputStreamLoader = this.getResources().openRawResource(R.raw.vocab_list);
        bufferedReaderLoader = new BufferedReader(new InputStreamReader(inputStreamLoader));

        // Count # of rows/lines in a file
        try {
            while (bufferedReaderCounter.readLine() != null) {
                intCount++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        vocabulary = new String[intCount];
        // load rows or lines into array
        try {
            for (int i = 0; i < intCount; i++) {
                String line = bufferedReaderLoader.readLine();
                vocabulary[i] = line.substring(0, 1).toUpperCase() + line.substring(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        ArrayAdapter<String> vocabAdapter = new ArrayAdapter<String>(this, R.layout.list_item, vocabulary);
        vocabList = findViewById(R.id.vocab_list);
        vocabList.setAdapter(vocabAdapter);

        vocabList.setOnItemClickListener(this);
    }

    // Slide animation when gobackBtn is clicked
    private void backToMenu() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // Slide animation when return button (the button next to home button) is clicked
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }


    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        TextView selectedWord = (TextView) adapterView.getChildAt(i);
        selectedWord.setTextColor(getColor(R.color.white));
        selectedWord.setBackgroundResource(R.drawable.button_gradient);

        PracticeActivity.word = selectedWord.getText().toString();

        Intent intent = new Intent(this, PracticeActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}