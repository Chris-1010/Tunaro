package com.ca.tunaro.adapters;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.List;

public class QueueLineDecoration extends RecyclerView.ItemDecoration {
    private final Paint paint;
    private final Song_RecyclerViewAdapter adapter;
    private boolean queueMatchesDisplay = false;

    public QueueLineDecoration(Song_RecyclerViewAdapter adapter) {
        this.adapter = adapter;
        paint = new Paint();
        paint.setColor(0x6632CD32);
        paint.setStrokeWidth(4f);
        paint.setAntiAlias(true);
    }

    public void setQueueMatchesDisplay(boolean matches) {
        queueMatchesDisplay = matches;
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        PlaybackManager pm = PlaybackManager.getInstance();
        List<SongModel> queue = pm.getQueue();
        int queueIndex = pm.getQueueIndex();
        SongModel currentSong = pm.getCurrentSong();

        if (!queueMatchesDisplay || queue.isEmpty() || queueIndex < 0 || currentSong == null) return;
        if (indexInQueueByUri(queue, currentSong) != queueIndex) return;

        ArrayList<SongModel> songs = adapter.getSongs();
        int centerX = parent.getWidth() / 2;

        for (int i = 0; i < parent.getChildCount() - 1; i++) {
            View child = parent.getChildAt(i);
            View nextChild = parent.getChildAt(i + 1);
            int pos = parent.getChildAdapterPosition(child);
            int nextPos = parent.getChildAdapterPosition(nextChild);
            if (pos < 0 || nextPos < 0 || pos >= songs.size() || nextPos >= songs.size()) continue;

            int songQueueIdx = indexInQueueByUri(queue, songs.get(pos));
            int nextSongQueueIdx = indexInQueueByUri(queue, songs.get(nextPos));

            if (songQueueIdx >= queueIndex && nextSongQueueIdx > queueIndex) {
                float top = child.getBottom();
                float bottom = nextChild.getTop() + nextChild.getHeight() / 2f;
                canvas.drawLine(centerX, top, centerX, bottom, paint);
            }
        }
    }

    private int indexInQueueByUri(List<SongModel> queue, SongModel song) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getUri().equals(song.getUri())) return i;
        }
        return -1;
    }
}
