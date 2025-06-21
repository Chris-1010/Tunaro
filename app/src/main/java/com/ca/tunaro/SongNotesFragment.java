package com.ca.tunaro;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SongNotesFragment extends Fragment {
    private SongModel song;
    private DatabaseHelper dbHelper;
    private Spinner noteTypeSpinner;
    private EditText noteInput;
    private RecyclerView notesRecyclerView;
    private SongNotesAdapter notesAdapter;
    private List<SongNote> notes = new ArrayList<>();

    public static SongNotesFragment newInstance(SongModel song) {
        SongNotesFragment fragment = new SongNotesFragment();
        Bundle args = new Bundle();
        args.putString("songId", song.getId());
        fragment.setArguments(args);
        fragment.song = song;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_song_notes, container, false);

        dbHelper = new DatabaseHelper(requireContext());

        // Initialize the add note button
        Button addNoteButton = view.findViewById(R.id.addNoteButton);
        notesRecyclerView = view.findViewById(R.id.notesRecyclerView);

        addNoteButton.setOnClickListener(v -> showAddNoteDialog());
        setupNotesList();
        loadNotes();

        return view;
    }

    private void setupNotesList() {
        notesAdapter = new SongNotesAdapter(requireContext(), new ArrayList<>());
        notesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        notesRecyclerView.setAdapter(notesAdapter);

        // Setup swipe functionality
        SwipeToDeleteCallback swipeHandler = new SwipeToDeleteCallback(notesAdapter,
                new SwipeToDeleteCallback.OnSwipeListener() {
                    @Override
                    public void onDelete(int position) {
                        SongNote noteToDelete = notesAdapter.getNote(position);
                        dbHelper.deleteNote(noteToDelete.getId());
                        notesAdapter.removeNote(position);
                        Toast.makeText(requireContext(), "Note deleted", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onEdit(int position) {
                        showEditDialog(notesAdapter.getNote(position));
                    }
                });
        new ItemTouchHelper(swipeHandler).attachToRecyclerView(notesRecyclerView);
    }

    private void saveNote() {
        String content = noteInput.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a note", Toast.LENGTH_SHORT).show();
            return;
        }

        String noteType = noteTypeSpinner.getSelectedItem().toString();
        SongNote note = new SongNote(null, song.getId(), noteType, content);

        long id = dbHelper.addNote(note);
        if (id != -1) {
            Toast.makeText(requireContext(), "Note added successfully", Toast.LENGTH_SHORT).show();
            noteInput.setText("");
            loadNotes(); // Refresh the notes list
        } else {
            Toast.makeText(requireContext(), "Error saving note", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadNotes() {
        notes = dbHelper.getSongNotes(song.getId());
        notesAdapter.updateNotes(notes);
    }

    private void showAddNoteDialog() {
        // Create dialog layout
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // Create edit text for note type
        final EditText typeInput = new EditText(requireContext());
        typeInput.setHint("Enter note type...");

        // Create RecyclerView for recent note types
        final RecyclerView recentTypesRecycler = new RecyclerView(requireContext());
        recentTypesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Create adapter for recent types
        final List<String> recentTypes = new ArrayList<>();
        final ArrayAdapter<String> recentTypesAdapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_list_item_1, recentTypes) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setTextSize(14f);
                textView.setPadding(20, 15, 20, 15);
                textView.setBackgroundResource(android.R.drawable.list_selector_background);
                return view;
            }
        };

        ListView recentTypesList = new ListView(requireContext());
        recentTypesList.setAdapter(recentTypesAdapter);
        recentTypesList.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 200));

        // Set up click listener for recent types
        recentTypesList.setOnItemClickListener((parent, view, position, id) -> {
            String selectedType = recentTypes.get(position);
            typeInput.setText(selectedType);
            typeInput.setSelection(selectedType.length()); // Move cursor to end
        });

        // Create edit text for content
        final EditText contentInput = new EditText(requireContext());
        contentInput.setHint("Enter your note...");

        // Add labels and views
        TextView typeLabel = new TextView(requireContext());
        typeLabel.setText("Note Type:");
        typeLabel.setPadding(0, 0, 0, 8);

        TextView recentLabel = new TextView(requireContext());
        recentLabel.setText("Recent Types:");
        recentLabel.setPadding(0, 16, 0, 8);
        recentLabel.setTextSize(12f);
        recentLabel.setTextColor(getResources().getColor(android.R.color.darker_gray));

        TextView contentLabel = new TextView(requireContext());
        contentLabel.setText("Content:");
        contentLabel.setPadding(0, 16, 0, 8);

        layout.addView(typeLabel);
        layout.addView(typeInput);
        layout.addView(recentLabel);
        layout.addView(recentTypesList);
        layout.addView(contentLabel);
        layout.addView(contentInput);

        // Handler for debounced search
        final Handler searchHandler = new Handler();
        final Runnable[] searchRunnable = new Runnable[1];

        // Initial load of recent types
        updateRecentTypes("", recentTypes, recentTypesAdapter);

        // Set up text watcher for debounced search
        typeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancel previous search
                if (searchRunnable[0] != null) {
                    searchHandler.removeCallbacks(searchRunnable[0]);
                }

                // Create new search runnable
                searchRunnable[0] = () -> updateRecentTypes(s.toString(), recentTypes, recentTypesAdapter);

                // Post delayed search (300ms debounce)
                searchHandler.postDelayed(searchRunnable[0], 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Show dialog
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Add Note")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String noteType = typeInput.getText().toString().trim();
                    String content = contentInput.getText().toString().trim();

                    if (noteType.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a note type", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (content.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a note", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SongNote note = new SongNote(null, song.getId(), noteType, content);

                    long id = dbHelper.addNote(note);
                    if (id != -1) {
                        Toast.makeText(requireContext(), "Note added successfully", Toast.LENGTH_SHORT).show();
                        loadNotes(); // Refresh the notes list
                    } else {
                        Toast.makeText(requireContext(), "Error saving note", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateRecentTypes(String searchTerm, List<String> recentTypes, ArrayAdapter<String> adapter) {
        List<String> newTypes = dbHelper.getRecentNoteTypes(searchTerm);
        recentTypes.clear();
        recentTypes.addAll(newTypes);
        adapter.notifyDataSetChanged();
    }

    private void showEditDialog(final SongNote note) {
        // Create dialog layout
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // Create edit text for note type
        final EditText typeInput = new EditText(requireContext());
        typeInput.setText(note.getNoteType());
        typeInput.setHint("Enter note type...");

        // Create RecyclerView for recent note types
        final List<String> recentTypes = new ArrayList<>();
        final ArrayAdapter<String> recentTypesAdapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_list_item_1, recentTypes) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setTextSize(14f);
                textView.setPadding(20, 15, 20, 15);
                textView.setBackgroundResource(android.R.drawable.list_selector_background);
                return view;
            }
        };

        ListView recentTypesList = new ListView(requireContext());
        recentTypesList.setAdapter(recentTypesAdapter);
        recentTypesList.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 200));

        // Set up click listener for recent types
        recentTypesList.setOnItemClickListener((parent, view, position, id) -> {
            String selectedType = recentTypes.get(position);
            typeInput.setText(selectedType);
            typeInput.setSelection(selectedType.length()); // Move cursor to end
        });

        // Create edit text for content
        final EditText contentInput = new EditText(requireContext());
        contentInput.setText(note.getContent());

        // Add labels and views
        TextView typeLabel = new TextView(requireContext());
        typeLabel.setText("Note Type:");
        typeLabel.setPadding(0, 0, 0, 8);

        TextView recentLabel = new TextView(requireContext());
        recentLabel.setText("Recent Types:");
        recentLabel.setPadding(0, 16, 0, 8);
        recentLabel.setTextSize(12f);
        recentLabel.setTextColor(getResources().getColor(android.R.color.darker_gray));

        TextView contentLabel = new TextView(requireContext());
        contentLabel.setText("Content:");
        contentLabel.setPadding(0, 16, 0, 8);

        layout.addView(typeLabel);
        layout.addView(typeInput);
        layout.addView(recentLabel);
        layout.addView(recentTypesList);
        layout.addView(contentLabel);
        layout.addView(contentInput);

        // Handler for debounced search
        final Handler searchHandler = new Handler();
        final Runnable[] searchRunnable = new Runnable[1];

        // Initial load of recent types
        updateRecentTypes("", recentTypes, recentTypesAdapter);

        // Set up text watcher for debounced search
        typeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancel previous search
                if (searchRunnable[0] != null) {
                    searchHandler.removeCallbacks(searchRunnable[0]);
                }

                // Create new search runnable
                searchRunnable[0] = () -> updateRecentTypes(s.toString(), recentTypes, recentTypesAdapter);

                // Post delayed search (300ms debounce)
                searchHandler.postDelayed(searchRunnable[0], 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Show dialog
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Edit Note")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String noteType = typeInput.getText().toString().trim();
                    String content = contentInput.getText().toString().trim();

                    if (noteType.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a note type", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (content.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a note", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    note.setNoteType(noteType);
                    note.setContent(content);
                    dbHelper.editNote(note);
                    loadNotes();
                    Toast.makeText(requireContext(), "Note updated", Toast.LENGTH_SHORT).show();
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
}