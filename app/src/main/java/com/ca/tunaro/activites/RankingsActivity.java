package com.ca.tunaro.activites;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ca.tunaro.R;

public class RankingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rankings);

        TextView placeholderText = findViewById(R.id.placeholder_text);
        Button backButton = findViewById(R.id.back_button);

        placeholderText.setText("Rankings feature coming soon!");

        backButton.setOnClickListener(v -> finish());
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.zoom_in, R.anim.zoom_out);
    }
}