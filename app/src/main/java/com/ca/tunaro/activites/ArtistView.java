package com.ca.tunaro.activites;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ca.tunaro.R;

public class ArtistView extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist_view);

        String artistName = getIntent().getStringExtra("artist_name");

        TextView titleView = findViewById(R.id.artist_view_title);
        if (artistName != null && !artistName.isEmpty()) {
            titleView.setText(artistName);
        }

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());
    }
}
