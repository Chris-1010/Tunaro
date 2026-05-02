package com.ca.tunaro.activites;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ca.tunaro.R;

public class AlbumView extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_view);

        String albumName = getIntent().getStringExtra("album_name");

        TextView titleView = findViewById(R.id.album_view_title);
        if (albumName != null && !albumName.isEmpty()) {
            titleView.setText(albumName);
        }

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());
    }
}
