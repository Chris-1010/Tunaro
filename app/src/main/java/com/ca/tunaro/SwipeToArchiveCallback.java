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

public class SwipeToArchiveCallback extends ItemTouchHelper.SimpleCallback {
    private final Playlist_RecyclerViewAdapter adapter;
    private final Drawable archiveIcon;
    private final ColorDrawable archiveBackground;
    private final int iconMargin;
    private final boolean isArchiveView;
    private final OnSwipeListener swipeListener;

    public interface OnSwipeListener {
        void onArchive(int position);
    }

    // Updated constructor that doesn't depend on PlayFragment
    public SwipeToArchiveCallback(Playlist_RecyclerViewAdapter adapter, OnSwipeListener listener, boolean isArchiveView) {
        super(0, ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        this.swipeListener = listener;
        this.isArchiveView = isArchiveView;

        archiveIcon = ContextCompat.getDrawable(adapter.getContext(),
                isArchiveView ? R.drawable.show : R.drawable.hide);
        archiveBackground = new ColorDrawable(isArchiveView ? Color.GREEN : Color.GRAY);
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
        if (direction == ItemTouchHelper.RIGHT) {
            swipeListener.onArchive(position);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;
        int itemHeight = itemView.getBottom() - itemView.getTop();

        if (dX > 0) {
            archiveBackground.setBounds(itemView.getLeft(),
                    itemView.getTop(), itemView.getLeft() + ((int) dX), itemView.getBottom());
            archiveBackground.draw(c);

            int iconTop = itemView.getTop() + (itemHeight - archiveIcon.getIntrinsicHeight()) / 2;
            int iconLeft = itemView.getLeft() + iconMargin;
            int iconRight = itemView.getLeft() + iconMargin + archiveIcon.getIntrinsicWidth();
            int iconBottom = iconTop + archiveIcon.getIntrinsicHeight();
            archiveIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
            archiveIcon.draw(c);
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}