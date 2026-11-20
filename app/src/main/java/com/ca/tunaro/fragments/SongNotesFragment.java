package com.ca.tunaro.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.R;
import com.ca.tunaro.adapters.SongNotesAdapter;
import com.ca.tunaro.callbacks.SwipeToDeleteCallback;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.models.SongNote;
import com.ca.tunaro.utils.AppExecutors;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.NoteEditorInput;
import com.ca.tunaro.utils.SnippetTheme;
import com.ca.tunaro.utils.StateMessage;
import com.ca.tunaro.utils.UiFeedback;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class SongNotesFragment extends Fragment {
    private static final long RECENT_TYPES_DEBOUNCE_MS = 300;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SongModel song;
    private DatabaseHelper dbHelper;
    /** Application context for messages sent from background threads; safe after detach. */
    private Context appContext;
    private RecyclerView notesRecyclerView;
    private Button addNoteButton;
    private SongNotesAdapter notesAdapter;

    private List<String> variantUris;
    private SnippetTheme currentTheme;
    /** Bumped per load so a slow, older read never overwrites a newer one. */
    private int loadGeneration;
    private boolean emptyStateBound;

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

        appContext = requireContext().getApplicationContext();
        dbHelper = DatabaseHelper.getInstance(appContext);
        if (getArguments() != null) {
            variantUris = getArguments().getStringArrayList("variantUris");
        }

        addNoteButton = view.findViewById(R.id.addNoteButton);
        notesRecyclerView = view.findViewById(R.id.notesRecyclerView);

        addNoteButton.setOnClickListener(v -> showNoteEditor(null));
        setupNotesList();
        loadNotes();
        applyNoteTheme();

        return view;
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    /** Post {@code task} to the main thread, dropping it if the view has gone. */
    private void postToUi(Runnable task) {
        mainHandler.post(() -> {
            if (isAdded() && getView() != null) task.run();
        });
    }

    /**
     * Theme the note rows and add-note button from the album art, matching the
     * snippet tab and the dynamic background used by {@code SongView}.
     */
    private void applyNoteTheme() {
        String coverUrl = song != null ? song.getAlbumCoverUrl() : null;
        if (coverUrl != null || song == null) {
            themeFromCover(coverUrl);
            return;
        }
        final String songId = song.getId();
        AppExecutors.io().execute(() -> {
            SongModel lean = dbHelper.getLeanSong(songId);
            String leanCover = lean != null ? lean.getAlbumCoverUrl() : null;
            postToUi(() -> themeFromCover(leanCover));
        });
    }

    private void themeFromCover(@Nullable String coverUrl) {
        if (coverUrl == null) {
            applyTheme(SnippetTheme.fallback());
            return;
        }
        ColorExtractor.extractColors(requireContext(), coverUrl, new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                if (!isAdded() || getView() == null) return;
                applyTheme(SnippetTheme.from(vibrantColor, dominantColor));
            }

            @Override
            public void onError() {
                if (!isAdded() || getView() == null) return;
                applyTheme(SnippetTheme.fallback());
            }
        });
    }

    private void applyTheme(SnippetTheme theme) {
        currentTheme = theme;
        notesAdapter.setTheme(theme);

        if (addNoteButton != null) {
            addNoteButton.setBackgroundTintList(
                    ColorStateList.valueOf(theme.playButton));
            addNoteButton.setTextColor(SnippetTheme.contrastColor(theme.playButton));
        }
    }

    private void setupNotesList() {
        notesAdapter = new SongNotesAdapter(requireContext(), new ArrayList<>());
        notesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        notesRecyclerView.setAdapter(notesAdapter);
        emptyStateBound = false;

        SwipeToDeleteCallback swipeHandler = new SwipeToDeleteCallback(notesAdapter,
                new SwipeToDeleteCallback.OnSwipeListener() {
                    @Override
                    public void onDelete(int position) {
                        SongNote noteToDelete = notesAdapter.getNote(position);
                        notesAdapter.removeNote(position);
                        updateNotesBadge(notesAdapter.getItemCount());
                        final long noteId = noteToDelete.getId();
                        AppExecutors.io().execute(() -> {
                            dbHelper.deleteNote(noteId);
                            UiFeedback.show(appContext, "Note deleted");
                        });
                    }

                    @Override
                    public void onEdit(int position) {
                        showNoteEditor(notesAdapter.getNote(position));
                    }
                });
        new ItemTouchHelper(swipeHandler).attachToRecyclerView(notesRecyclerView);
    }

    /** Read the notes off the main thread, then refresh the list and the tab badge. */
    private void loadNotes() {
        if (song == null) return;
        final int generation = ++loadGeneration;
        final List<String> uris = variantUris != null ? new ArrayList<>(variantUris) : null;
        final String songId = song.getId();
        AppExecutors.io().execute(() -> {
            List<SongNote> loaded = uris != null && uris.size() > 1
                    ? dbHelper.getSongNotesForUris(uris)
                    : dbHelper.getSongNotes(songId);
            postToUi(() -> {
                if (generation != loadGeneration) return;
                notesAdapter.updateNotes(loaded);
                if (!emptyStateBound) {
                    // Bound after the first read so "No notes yet" never flashes while loading.
                    emptyStateBound = true;
                    StateMessage.bindEmptyState(notesAdapter,
                            ((View) notesRecyclerView.getParent()).findViewById(R.id.notes_empty),
                            R.drawable.ic_state_note, R.string.notes_empty, R.string.notes_empty_hint);
                }
                updateNotesBadge(loaded.size());
            });
        });
    }

    /**
     * Show the shared add/edit dialog. {@code existing} null means add. Save validates in place
     * and keeps the dialog (and the typed text) open until both fields are filled.
     */
    private void showNoteEditor(@Nullable final SongNote existing) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_note_editor, null, false);
        final TextInputLayout typeLayout = content.findViewById(R.id.noteTypeLayout);
        final TextInputLayout contentLayout = content.findViewById(R.id.noteContentLayout);
        final EditText typeInput = content.findViewById(R.id.noteTypeInput);
        final EditText contentInput = content.findViewById(R.id.noteContentInput);
        final TextView recentLabel = content.findViewById(R.id.recentTypesLabel);
        final ChipGroup recentGroup = content.findViewById(R.id.recentTypesGroup);

        if (existing != null) {
            typeInput.setText(existing.getNoteType());
            contentInput.setText(existing.getContent());
        }
        if (currentTheme != null) {
            ColorStateList accent = ColorStateList.valueOf(currentTheme.seekbarProgress);
            typeLayout.setBoxStrokeColorStateList(accent);
            contentLayout.setBoxStrokeColorStateList(accent);
            typeLayout.setHintTextColor(accent);
            contentLayout.setHintTextColor(accent);
        }

        final Handler searchHandler = new Handler(Looper.getMainLooper());
        final int[] searchGeneration = {0};
        final Runnable[] pendingSearch = new Runnable[1];

        final Runnable runSearch = () -> {
            final int generation = ++searchGeneration[0];
            final String term = NoteEditorInput.trimmed(typeInput.getText());
            AppExecutors.io().execute(() -> {
                List<String> types = dbHelper.getRecentNoteTypes(term);
                searchHandler.post(() -> {
                    if (generation != searchGeneration[0] || !isAdded()) return;
                    bindRecentTypes(types, recentGroup, recentLabel, typeInput, contentInput);
                });
            });
        };
        pendingSearch[0] = runSearch;
        runSearch.run();

        typeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                typeLayout.setError(null);
                searchHandler.removeCallbacks(pendingSearch[0]);
                searchHandler.postDelayed(pendingSearch[0], RECENT_TYPES_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        contentInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                contentLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? R.string.note_add_title : R.string.note_edit_title)
                .setView(content)
                // Listener is replaced after show() so a failed validation keeps the dialog open.
                .setPositiveButton(R.string.note_save, null)
                .setNegativeButton(R.string.note_cancel, null)
                .create();

        dialog.setOnDismissListener(d -> {
            // Stop pending lookups and drop late results for this dialog.
            searchHandler.removeCallbacksAndMessages(null);
            searchGeneration[0]++;
            // Return a swiped-to-edit row to rest.
            if (existing != null && notesAdapter != null) {
                int position = notesAdapter.indexOfNote(existing.getId());
                if (position != -1) notesAdapter.notifyItemChanged(position);
            }
        });
        dialog.setOnShowListener(d -> themeDialog(dialog));
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();

        // Focus the first field that still needs input.
        EditText focusTarget = existing == null ? typeInput : contentInput;
        focusTarget.requestFocus();
        focusTarget.setSelection(focusTarget.length());

        contentInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int result = NoteEditorInput.validate(typeInput.getText(), contentInput.getText());
            if ((result & NoteEditorInput.MISSING_TYPE) != 0) {
                typeLayout.setError(getString(R.string.note_type_required));
            }
            if ((result & NoteEditorInput.MISSING_CONTENT) != 0) {
                contentLayout.setError(getString(R.string.note_content_required));
            }
            if (result != NoteEditorInput.VALID) {
                ((result & NoteEditorInput.MISSING_TYPE) != 0 ? typeInput : contentInput).requestFocus();
                return;
            }
            String noteType = NoteEditorInput.trimmed(typeInput.getText());
            String noteContent = NoteEditorInput.trimmed(contentInput.getText());
            dialog.dismiss();
            if (existing == null) {
                addNote(noteType, noteContent);
            } else {
                editNote(existing, noteType, noteContent);
            }
        });
    }

    private void bindRecentTypes(List<String> types, ChipGroup group, TextView label,
                                 EditText typeInput, EditText contentInput) {
        group.removeAllViews();
        int visibility = types.isEmpty() ? View.GONE : View.VISIBLE;
        group.setVisibility(visibility);
        label.setVisibility(visibility);
        ColorStateList chipBg = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.surface));
        ColorStateList chipStroke = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.divider));
        for (String type : types) {
            Chip chip = new Chip(requireContext());
            chip.setText(type);
            chip.setTextAppearance(R.style.TextAppearance_Tunaro_BodySmall);
            chip.setChipBackgroundColor(chipBg);
            chip.setChipStrokeColor(chipStroke);
            chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
            chip.setEnsureMinTouchTargetSize(true);
            chip.setOnClickListener(v -> {
                typeInput.setText(type);
                typeInput.setSelection(type.length());
                contentInput.requestFocus();
            });
            group.addView(chip);
        }
    }

    private void addNote(String noteType, String noteContent) {
        final SongNote note = new SongNote(null, song.getId(), noteType, noteContent);
        AppExecutors.io().execute(() -> {
            long id = dbHelper.addNote(note);
            if (id == -1) {
                UiFeedback.show(appContext, "Couldn't save the note");
                return;
            }
            postToUi(this::loadNotes);
        });
    }

    private void editNote(SongNote existing, String noteType, String noteContent) {
        // Edit a copy so the row bound in the adapter stays a faithful snapshot for diffing.
        final SongNote updated = new SongNote(existing.getUuid(), existing.getId(), existing.getSongId(),
                noteType, noteContent, existing.getTimestamp());
        AppExecutors.io().execute(() -> {
            dbHelper.editNote(updated);
            UiFeedback.show(appContext, "Note updated");
            postToUi(this::loadNotes);
        });
    }

    /**
     * Tint an AlertDialog's window background and buttons to the album theme. Call once shown.
     */
    private void themeDialog(AlertDialog dialog) {
        int base = ContextCompat.getColor(requireContext(), R.color.surface_raised);
        int cardColor = currentTheme == null ? base
                : androidx.core.graphics.ColorUtils.compositeColors(currentTheme.rowBackground, base);
        if (dialog.getWindow() != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cardColor);
            bg.setCornerRadius(getResources().getDimension(R.dimen.radius_card));
            dialog.getWindow().setBackgroundDrawable(bg);
        }
        int textPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary);
        Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (pos != null) pos.setTextColor(currentTheme != null ? currentTheme.seekbarProgress
                : ContextCompat.getColor(requireContext(), R.color.accent));
        if (neg != null) neg.setTextColor(currentTheme != null ? currentTheme.primaryText : textPrimary);

        // AppCompat dialogs use the support "alertTitle" id, not the framework one.
        View title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (title instanceof TextView) ((TextView) title).setTextColor(textPrimary);
    }

    private void updateNotesBadge(int count) {
        if (getActivity() instanceof com.ca.tunaro.activites.SongView) {
            ((com.ca.tunaro.activites.SongView) getActivity()).updateTabBadge(1, count);
        }
    }
}
