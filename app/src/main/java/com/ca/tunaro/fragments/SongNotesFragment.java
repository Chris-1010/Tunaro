package com.ca.tunaro.fragments;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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

import com.ca.tunaro.R;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.models.SongNote;
import com.ca.tunaro.adapters.SongNotesAdapter;
import com.ca.tunaro.callbacks.SwipeToDeleteCallback;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.SnippetTheme;

import java.util.ArrayList;
import java.util.List;

public class SongNotesFragment extends Fragment {
    private static final String TAG = "SongNotesFragment";

    private SongModel song;
    private DatabaseHelper dbHelper;
    private Spinner noteTypeSpinner;
    private EditText noteInput;
    private RecyclerView notesRecyclerView;
    private Button addNoteButton;
    private SongNotesAdapter notesAdapter;
    private List<SongNote> notes = new ArrayList<>();

    private List<String> variantUris;
    private SnippetTheme currentTheme;

    public static SongNotesFragment newInstance(SongModel song, List<String> variantUris) {
        SongNotesFragment fragment = new SongNotesFragment();
        Bundle args = new Bundle();
        args.putString("songId", song.getId());
        args.putStringArrayList("variantUris", new java.util.ArrayList<>(variantUris));
        fragment.setArguments(args);
        fragment.song = song;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_song_notes, container, false);

        dbHelper = new DatabaseHelper(requireContext());
        if (getArguments() != null) {
            variantUris = getArguments().getStringArrayList("variantUris");
        }

        // Initialize the add note button
        addNoteButton = view.findViewById(R.id.addNoteButton);
        notesRecyclerView = view.findViewById(R.id.notesRecyclerView);

        addNoteButton.setOnClickListener(v -> showAddNoteDialog());
        setupNotesList();
        loadNotes();
        applyNoteTheme();

        return view;
    }

    /**
     * Theme the note rows and add-note button from the album art, matching the
     * snippet tab and the dynamic background used by {@code SongView}.
     */
    private void applyNoteTheme() {
        String coverUrl = song != null ? song.getAlbumCoverUrl() : null;
        if (coverUrl == null && song != null) {
            SongModel lean = dbHelper.getLeanSong(song.getId());
            if (lean != null) coverUrl = lean.getAlbumCoverUrl();
        }
        if (coverUrl == null) {
            applyTheme(SnippetTheme.fallback());
            return;
        }
        ColorExtractor.extractColors(requireContext(), coverUrl, new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                if (!isAdded()) return;
                applyTheme(SnippetTheme.from(vibrantColor, dominantColor));
            }

            @Override
            public void onError() {
                if (!isAdded()) return;
                applyTheme(SnippetTheme.fallback());
            }
        });
    }

    private void applyTheme(SnippetTheme theme) {
        currentTheme = theme;
        notesAdapter.setTheme(theme);

        if (addNoteButton != null) {
            addNoteButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(theme.playButton));
            addNoteButton.setTextColor(SnippetTheme.contrastColor(theme.playButton));
        }
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
                        updateNotesBadge();
                        showToast("Note deleted");
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
            showToast("Please enter a note");
            return;
        }

        String noteType = noteTypeSpinner.getSelectedItem().toString();
        SongNote note = new SongNote(null, song.getId(), noteType, content);

        long id = dbHelper.addNote(note);
        if (id != -1) {
            noteInput.setText("");
            loadNotes(); // Refresh the notes list
            updateNotesBadge();
        } else {
            showToast("Error saving note");
        }
    }

    private void loadNotes() {
        notes = variantUris != null && variantUris.size() > 1
                ? dbHelper.getSongNotesForUris(variantUris)
                : dbHelper.getSongNotes(song.getId());
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
                textView.setTextColor(Color.WHITE);
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
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Add Note")
                .setView(layout)
                .setPositiveButton("Save", (d, which) -> {
                    String noteType = typeInput.getText().toString().trim();
                    String content = contentInput.getText().toString().trim();

                    if (noteType.isEmpty()) {
                        showToast("Please enter a note type");
                        return;
                    }

                    if (content.isEmpty()) {
                        showToast("Please enter a note");
                        return;
                    }

                    SongNote note = new SongNote(null, song.getId(), noteType, content);

                    long id = dbHelper.addNote(note);
                    if (id != -1) {
                        loadNotes(); // Refresh the notes list
                        updateNotesBadge();
                    } else {
                        showToast("Error saving note");
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        whitenDialogText(layout);
        themeDialog(dialog);
        dialog.show();
    }

    /**
     * Recursively set text/hint colours to white-ish on the dialog content so
     * labels and inputs read against the dark themed background.
     */
    private void whitenDialogText(View view) {
        if (view instanceof EditText) {
            EditText et = (EditText) view;
            et.setTextColor(Color.WHITE);
            et.setHintTextColor(Color.parseColor("#B3FFFFFF"));
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(Color.WHITE);
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                whitenDialogText(vg.getChildAt(i));
            }
        }
    }

    /**
     * Tint an AlertDialog's window background and buttons to the album theme.
     */
    private void themeDialog(androidx.appcompat.app.AlertDialog dialog) {
        if (currentTheme == null) return;
        int cardColor = androidx.core.graphics.ColorUtils.compositeColors(
                currentTheme.rowBackground, Color.parseColor("#1A1A1A"));
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(cardColor);
                bg.setCornerRadius(dpToPx(12));
                dialog.getWindow().setBackgroundDrawable(bg);
            }
            android.widget.Button pos = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            android.widget.Button neg = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
            if (pos != null) pos.setTextColor(currentTheme.seekbarProgress);
            if (neg != null) neg.setTextColor(currentTheme.primaryText);

            // Whiten the dialog title. AppCompat dialogs use the support
            // "alertTitle" id, not the framework one.
            View title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
            if (title == null) {
                int frameworkId = requireContext().getResources()
                        .getIdentifier("alertTitle", "id", "android");
                if (frameworkId != 0) title = dialog.findViewById(frameworkId);
            }
            if (title instanceof TextView) ((TextView) title).setTextColor(Color.WHITE);
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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
                textView.setTextColor(Color.WHITE);
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
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Edit Note")
                .setView(layout)
                .setPositiveButton("Save", (d, which) -> {
                    String noteType = typeInput.getText().toString().trim();
                    String content = contentInput.getText().toString().trim();

                    if (noteType.isEmpty()) {
                        showToast("Please enter a note type");
                        return;
                    }

                    if (content.isEmpty()) {
                        showToast("Please enter a note");
                        return;
                    }

                    note.setNoteType(noteType);
                    note.setContent(content);
                    dbHelper.editNote(note);
                    loadNotes();
                    showToast("Note updated");
                })
                .setNegativeButton("Cancel", (d, which) -> {
                    // Reset the view state by notifying adapter of change
                    int position = notes.indexOf(note);
                    if (position != -1) {
                        notesAdapter.notifyItemChanged(position);
                    }
                })
                .create();
        whitenDialogText(layout);
        themeDialog(dialog);
        dialog.show();
    }

    private void updateNotesBadge() {
        if (getActivity() instanceof com.ca.tunaro.activites.SongView) {
            int count = (variantUris != null && variantUris.size() > 1
                    ? dbHelper.getSongNotesForUris(variantUris)
                    : dbHelper.getSongNotes(song.getId())).size();
            ((com.ca.tunaro.activites.SongView) getActivity()).updateTabBadge(1, count);
        }
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}