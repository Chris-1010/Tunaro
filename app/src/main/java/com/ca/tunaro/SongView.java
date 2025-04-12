package com.ca.tunaro;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.spotify.android.appremote.api.SpotifyAppRemote;

import java.util.ArrayList;
import java.util.List;

public class SongView extends AppCompatActivity {
    // Fields
    private SongModel selectedSong;
    private DatabaseHelper dbHelper;
    private Spinner noteTypeSpinner;
    private EditText noteInput;
    private Button addNoteButton;
    private LinearLayout noteInputLayout;
    private RecyclerView notesRecyclerView;
    private SongNotesAdapter notesAdapter;
    private List<SongNote> notes = new ArrayList<>();

    // Creation
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_song_view);

        dbHelper = new DatabaseHelper(this);

        // Initialize UI components
        initializeUI();
        setupNoteTypeSpinner();
        setupNotesList();

        // Add play button functionality
        ImageView playButton = findViewById(R.id.play_button);
        playButton.setOnClickListener(v -> playSong());

        // Load existing notes
        loadExistingNotes();
    }

    // Initialization
    private void initializeUI() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Retrieve the selected song
        selectedSong = SelectedSongHolder.getInstance().getSelectedSong();
        if (selectedSong == null) {
            finish();
            return;
        }

        // Set up basic song info
        setupBasicSongInfo();

        // Initialize note-related UI components
        noteTypeSpinner = findViewById(R.id.noteTypeSpinner);
        noteInput = findViewById(R.id.noteInput);
        addNoteButton = findViewById(R.id.addNoteButton);
        noteInputLayout = findViewById(R.id.noteInputLayout);
        notesRecyclerView = findViewById(R.id.notesRecyclerView);
    }

    // Setup methods
    private void setupBasicSongInfo() {
        String name = selectedSong.getName();
        String artist = selectedSong.getArtist();
        String albumCover = selectedSong.getAlbumCoverUrl();
        String albumName = selectedSong.getAlbumName();
        String duration = selectedSong.getDurationString();

        TextView nameView = findViewById(R.id.SongView_SongName);
        TextView artistView = findViewById(R.id.SongView_ArtistName);
        ImageView albumCoverImageView = findViewById(R.id.SongView_AlbumCover);
        TextView albumView = findViewById(R.id.SongView_AlbumName);
        TextView durationView = findViewById(R.id.SongView_SongDuration);

        nameView.setText(name);
        artistView.setText(artist);
        Glide.with(this)
                .load(albumCover)
                .into(albumCoverImageView);
        albumView.setText(albumName);
        durationView.setText(duration);
    }

    private void setupNoteTypeSpinner() {
        ArrayList<String> noteTypes = new ArrayList<>();
        for (SongNote.NoteType type : SongNote.NoteType.values()) {
            noteTypes.add(type.getDisplayName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, noteTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        noteTypeSpinner.setAdapter(adapter);

        noteTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                noteInputLayout.setVisibility(View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                noteInputLayout.setVisibility(View.GONE);
            }
        });

        addNoteButton.setOnClickListener(v -> saveNote());
    }

    private void setupNotesList() {
        notesAdapter = new SongNotesAdapter(this, new ArrayList<>());
        notesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notesRecyclerView.setAdapter(notesAdapter);

        // Setup swipe functionality
        SwipeToDeleteCallback swipeHandler = new SwipeToDeleteCallback(notesAdapter,
                new SwipeToDeleteCallback.OnSwipeListener() {
                    @Override
                    public void onDelete(int position) {
                        SongNote noteToDelete = notesAdapter.getNote(position);
                        dbHelper.deleteNote(noteToDelete.getId());
                        notesAdapter.removeNote(position);
                        Toast.makeText(SongView.this, "Note deleted", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onEdit(int position) {
                        showEditDialog(notesAdapter.getNote(position));
                    }
                });
        new ItemTouchHelper(swipeHandler).attachToRecyclerView(notesRecyclerView);
    }

    // Save methods
    private void saveNote() {
        String content = noteInput.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "Please enter a note", Toast.LENGTH_SHORT).show();
            return;
        }

        String noteType = noteTypeSpinner.getSelectedItem().toString();
        SongNote note = new SongNote(selectedSong.getId(), noteType, content);

        long id = dbHelper.addNote(note);
        if (id != -1) {
            Toast.makeText(this, "Note added successfully", Toast.LENGTH_SHORT).show();
            noteInput.setText("");
            loadExistingNotes(); // Refresh the notes list
        } else {
            Toast.makeText(this, "Error saving note", Toast.LENGTH_SHORT).show();
        }
    }

    // Load methods
    private void loadExistingNotes() {
        notes = dbHelper.getSongNotes(selectedSong.getId());
        notesAdapter.updateNotes(notes);
    }

    private void playSong() {
        SpotifyAppRemote mSpotifyAppRemote = SelectedPlaylistHolder.getInstance().getSpotifyAppRemote();
        MainActivity mainActivity = SelectedPlaylistHolder.getInstance().getMainActivity();

        // Try to reconnect Spotify if MainActivity is available
        if (mainActivity != null && !mSpotifyAppRemote.isConnected()) {
            Toast.makeText(this, "Attempting to reconnect to Spotify...", Toast.LENGTH_SHORT).show();
            // Call a method in MainActivity to reconnect
            mainActivity.connectSpotifyAppRemote();
        }

        if (mSpotifyAppRemote != null && mSpotifyAppRemote.isConnected() && selectedSong != null) {
            try {
                // Play the song
                mSpotifyAppRemote.getPlayerApi().play(selectedSong.getUri())
                        .setResultCallback(empty -> {
                            // Create a "Date Listened" note
//                            String currentDate = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm")
//                                    .format(new java.util.Date());
//                            SongNote note = new SongNote(
//                                    selectedSong.getId(),
//                                    SongNote.NoteType.DATE_LISTENED.getDisplayName(),
//                                    currentDate
//                            );
//
//                            // Save to database
//                            dbHelper.addNote(note);
//
//                            // Refresh notes display
//                            loadExistingNotes();
//
                            Toast.makeText(this, "Playing " + selectedSong.getName(),
                                    Toast.LENGTH_SHORT).show();
                        })
                        .setErrorCallback(throwable -> {
                            Toast.makeText(this, "Error playing song: " + throwable.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            Log.e("SongView", "PlaybackError: " + throwable.getMessage());
                        });
            } catch (Exception e) {
                Log.e("SongView", "PlaybackException: " + e.getMessage());
            }
        } else {
            Toast.makeText(this, "Unable to play song. Please check Spotify connection.",
                    Toast.LENGTH_SHORT).show();
        }
    }


    // Display methods
    private void showEditDialog(final SongNote note) {
        // Create dialog layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // Create spinner for note type
        final Spinner typeSpinner = new Spinner(this);
        ArrayList<String> noteTypes = new ArrayList<>();
        for (SongNote.NoteType type : SongNote.NoteType.values()) {
            noteTypes.add(type.getDisplayName());
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, noteTypes);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(spinnerAdapter);
        typeSpinner.setSelection(noteTypes.indexOf(note.getNoteType()));

        // Create edit text for content
        final EditText contentInput = new EditText(this);
        contentInput.setText(note.getContent());

        layout.addView(typeSpinner);
        layout.addView(contentInput);

        // Show dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit Note")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    note.setNoteType(typeSpinner.getSelectedItem().toString());
                    note.setContent(contentInput.getText().toString());
                    dbHelper.editNote(note);
                    loadExistingNotes();
                    Toast.makeText(SongView.this, "Note updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Reset the view state by notifying adapter of change
                    int position = notes.indexOf(note);
                    if (position != -1) {
                        notesAdapter.notifyItemChanged(position);
                    }
                })
                .show();
    }

    // Destroy
    @Override
    protected void onDestroy() {
        super.onDestroy();
        SelectedSongHolder.getInstance().clearSelectedSong();
    }
}