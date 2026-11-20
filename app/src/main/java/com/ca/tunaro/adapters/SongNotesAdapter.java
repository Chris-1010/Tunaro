package com.ca.tunaro.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.R;
import com.ca.tunaro.models.SongNote;
import com.ca.tunaro.utils.NoteEditorInput;
import com.ca.tunaro.utils.SnippetTheme;

import java.util.ArrayList;
import java.util.List;

public class SongNotesAdapter extends RecyclerView.Adapter<SongNotesAdapter.NoteViewHolder> {
    private final Context context;
    private final List<SongNote> notes = new ArrayList<>();
    private SnippetTheme theme = SnippetTheme.fallback();

    public SongNotesAdapter(Context context, List<SongNote> notes) {
        this.context = context;
        this.notes.addAll(notes);
    }

    public void setTheme(SnippetTheme theme) {
        this.theme = theme;
        notifyItemRangeChanged(0, notes.size());
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.note_item, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        SongNote note = notes.get(position);
        holder.noteTypeText.setText(note.getNoteType());
        holder.noteContentText.setText(note.getContent());
        holder.timestampText.setText(note.getTimestamp());

        applyTheme(holder);
    }

    private void applyTheme(NoteViewHolder holder) {
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(dpToPx(holder.itemView, 8));
        rowBg.setColor(theme.rowBackground);
        rowBg.setStroke(dpToPx(holder.itemView, 1), theme.border);
        holder.rowContainer.setBackground(rowBg);

        // Note type uses the vibrant accent; body/timestamp follow row text.
        holder.noteTypeText.setTextColor(theme.seekbarProgress);
        holder.noteContentText.setTextColor(theme.primaryText);
        holder.timestampText.setTextColor(theme.secondaryText);
    }

    private int dpToPx(View view, int dp) {
        return Math.round(dp * view.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    /** Replace the list, animating only the rows that were added, removed or edited. */
    public void updateNotes(List<SongNote> newNotes) {
        final List<SongNote> oldNotes = new ArrayList<>(notes);
        final List<SongNote> next = new ArrayList<>(newNotes);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldNotes.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return NoteEditorInput.sameNote(oldNotes.get(oldPos), next.get(newPos));
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return NoteEditorInput.sameContents(oldNotes.get(oldPos), next.get(newPos));
            }
        });
        notes.clear();
        notes.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    /** Adapter position of the note with {@code noteId}, or -1. */
    public int indexOfNote(long noteId) {
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).getId() == noteId) return i;
        }
        return -1;
    }

    public Context getContext() {
        return context;
    }

    public SongNote getNote(int position) {
        return notes.get(position);
    }

    public void removeNote(int position) {
        notes.remove(position);
        notifyItemRemoved(position);
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        View rowContainer;
        TextView noteTypeText;
        TextView noteContentText;
        TextView timestampText;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            rowContainer = itemView.findViewById(R.id.noteRowContainer);
            noteTypeText = itemView.findViewById(R.id.noteTypeText);
            noteContentText = itemView.findViewById(R.id.noteContentText);
            timestampText = itemView.findViewById(R.id.timestampText);
        }
    }
}