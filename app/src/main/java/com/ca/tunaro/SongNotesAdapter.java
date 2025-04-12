package com.ca.tunaro;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SongNotesAdapter extends RecyclerView.Adapter<SongNotesAdapter.NoteViewHolder> {
    private final Context context;
    private List<SongNote> notes;

    public SongNotesAdapter(Context context, List<SongNote> notes) {
        this.context = context;
        this.notes = notes;
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
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    public void updateNotes(List<SongNote> newNotes) {
        this.notes = newNotes;
        notifyDataSetChanged();
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
        TextView noteTypeText;
        TextView noteContentText;
        TextView timestampText;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            noteTypeText = itemView.findViewById(R.id.noteTypeText);
            noteContentText = itemView.findViewById(R.id.noteContentText);
            timestampText = itemView.findViewById(R.id.timestampText);
        }
    }
}