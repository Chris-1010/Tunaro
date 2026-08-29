package com.ca.tunaro.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.R;

/**
 * A RecyclerView that grows to fit its content up to a maximum height, then
 * scrolls. Used for the short snippet lists under each ranking contender: one
 * snippet shows a single tight row, many snippets scroll within a fixed cap.
 */
public class MaxHeightRecyclerView extends RecyclerView {
    private int maxHeightPx = 0;

    public MaxHeightRecyclerView(@NonNull Context context) {
        super(context);
    }

    public MaxHeightRecyclerView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        readMaxHeight(context, attrs);
    }

    public MaxHeightRecyclerView(@NonNull Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        readMaxHeight(context, attrs);
    }

    private void readMaxHeight(Context context, AttributeSet attrs) {
        android.content.res.TypedArray a =
                context.obtainStyledAttributes(attrs, R.styleable.MaxHeightRecyclerView);
        maxHeightPx = a.getDimensionPixelSize(R.styleable.MaxHeightRecyclerView_maxHeight, 0);
        a.recycle();
    }

    public void setMaxHeightPx(int maxHeightPx) {
        this.maxHeightPx = maxHeightPx;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (maxHeightPx > 0) {
            heightSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
