package com.ca.tunaro;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

        // Create spinner for note type
        final Spinner typeSpinner = new Spinner(requireContext());
        ArrayList<String> noteTypes = new ArrayList<>();
        for (SongNote.NoteType type : SongNote.NoteType.values()) {
            noteTypes.add(type.getDisplayName());
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, noteTypes);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(spinnerAdapter);

        // Create edit text for content
        final EditText contentInput = new EditText(requireContext());
        contentInput.setHint("Enter your note...");

        // Add a label for the spinner
        TextView typeLabel = new TextView(requireContext());
        typeLabel.setText("Note Type:");
        typeLabel.setPadding(0, 0, 0, 8);

        // Add a label for the content
        TextView contentLabel = new TextView(requireContext());
        contentLabel.setText("Content:");
        contentLabel.setPadding(0, 16, 0, 8);

        layout.addView(typeLabel);
        layout.addView(typeSpinner);
        layout.addView(contentLabel);
        layout.addView(contentInput);

        // Show dialog
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Add Note")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String content = contentInput.getText().toString().trim();
                    if (content.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a note", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String selectedNoteType = typeSpinner.getSelectedItem().toString();
                    SongNote note = new SongNote(null, song.getId(), selectedNoteType, content);

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

    private void showEditDialog(final SongNote note) {
        // Create dialog layout
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // Create spinner for note type
        final Spinner typeSpinner = new Spinner(requireContext());
        ArrayList<String> noteTypes = new ArrayList<>();
        for (SongNote.NoteType type : SongNote.NoteType.values()) {
            noteTypes.add(type.getDisplayName());
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, noteTypes);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(spinnerAdapter);
        typeSpinner.setSelection(noteTypes.indexOf(note.getNoteType()));

        // Create edit text for content
        final EditText contentInput = new EditText(requireContext());
        contentInput.setText(note.getContent());

        layout.addView(typeSpinner);
        layout.addView(contentInput);

        // Show dialog
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Edit Note")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    note.setNoteType(typeSpinner.getSelectedItem().toString());
                    note.setContent(contentInput.getText().toString());
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