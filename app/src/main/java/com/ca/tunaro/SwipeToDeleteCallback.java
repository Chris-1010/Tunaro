package com.ca.tunaro;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {
    private final SongNotesAdapter adapter;
    private final Drawable deleteIcon;
    private final Drawable editIcon;
    private final ColorDrawable deleteBackground;
    private final ColorDrawable editBackground;
    private final int iconMargin;
    private final OnSwipeListener swipeListener;

    public interface OnSwipeListener {
        void onDelete(int position);
        void onEdit(int position);
    }

    public SwipeToDeleteCallback(SongNotesAdapter adapter, OnSwipeListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        this.swipeListener = listener;

        deleteIcon = ContextCompat.getDrawable(adapter.getContext(), android.R.drawable.ic_menu_delete);
        editIcon = ContextCompat.getDrawable(adapter.getContext(), android.R.drawable.ic_menu_edit);
        deleteBackground = new ColorDrawable(Color.RED);
        editBackground = new ColorDrawable(Color.BLUE);
        iconMargin = 16;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();
        if (direction == ItemTouchHelper.LEFT) {
            swipeListener.onDelete(position);
        } else if (direction == ItemTouchHelper.RIGHT) {
            swipeListener.onEdit(position);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {

        View itemView = viewHolder.itemView;
        int itemHeight = itemView.getBottom() - itemView.getTop();

        if (dX > 0) { // Swiping to the right (edit)
            editBackground.setBounds(itemView.getLeft(), itemView.getTop(),
                    itemView.getLeft() + ((int) dX), itemView.getBottom());
            editBackground.draw(c);

            int iconTop = itemView.getTop() + (itemHeight - editIcon.getIntrinsicHeight()) / 2;
            int iconLeft = itemView.getLeft() + iconMargin;
            int iconRight = itemView.getLeft() + iconMargin + editIcon.getIntrinsicWidth();
            int iconBottom = iconTop + editIcon.getIntrinsicHeight();
            editIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
            editIcon.draw(c);
        } else if (dX < 0) { // Swiping to the left (delete)
            deleteBackground.setBounds(itemView.getRight() + ((int) dX),
                    itemView.getTop(), itemView.getRight(), itemView.getBottom());
            deleteBackground.draw(c);

            int iconTop = itemView.getTop() + (itemHeight - deleteIcon.getIntrinsicHeight()) / 2;
            int iconRight = itemView.getRight() - iconMargin;
            int iconLeft = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth();
            int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
            deleteIcon.draw(c);
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}