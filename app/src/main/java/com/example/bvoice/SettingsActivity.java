package com.example.bvoice;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {
    ImageView exitBtn;

    String [] SignLanguage = {"American", "Vietnamese"};
    String [] TextLanguage = {"English", "Vietnamese"};
    AutoCompleteTextView signAutoComplete, textAutoComplete;
    ArrayAdapter<String> signAdapter, textAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        signAutoComplete = findViewById(R.id.sign_language);
        signAdapter = new ArrayAdapter<>(this, R.layout.drop_down_item , SignLanguage);
        signAutoComplete.setAdapter(signAdapter);

        signAutoComplete.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String item = adapterView.getItemAtPosition(i).toString();
                Toast.makeText(SettingsActivity.this, "Sign language: " + item, Toast.LENGTH_SHORT).show();
            }
        });

        textAutoComplete = findViewById(R.id.text_language);
        textAdapter = new ArrayAdapter<>(this, R.layout.drop_down_item, TextLanguage);
        textAutoComplete.setAdapter(textAdapter);

        textAutoComplete.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String item = adapterView.getItemAtPosition(i).toString();
                Toast.makeText(SettingsActivity.this, "Text language: " + item, Toast.LENGTH_SHORT).show();
            }
        });

        exitBtn = findViewById(R.id.exit_settings);
        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backToMenu();
            }
        });
    }

    private void backToMenu() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_top, R.anim.slide_out_bottom);
    }

    // Slide animation when return button (the button next to home button) is clicked
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_top, R.anim.slide_out_bottom);
    }
}