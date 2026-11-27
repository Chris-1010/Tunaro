package com.ca.tunaro.utils;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.ca.tunaro.R;

import java.util.List;

/**
 * Dialog presenting a tappable list of items (Developer screen command history,
 * SongView variant picker). Surface, corners and text colours come from the app's
 * alertDialogTheme.
 */
public class DarkListDialog {

    public interface OnEntrySelected {
        void onSelected(int position);
    }

    public static void show(Context context, String title, List<String> items, OnEntrySelected listener) {
        float density = context.getResources().getDisplayMetrics().density;

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextAppearance(R.style.TextAppearance_Tunaro_Title);
        int pad = context.getResources().getDimensionPixelSize(R.dimen.space_lg);
        titleView.setPadding(pad, pad, pad,
                context.getResources().getDimensionPixelSize(R.dimen.space_sm));

        ListView listView = new ListView(context);
        listView.setAdapter(new ArrayAdapter<>(context, R.layout.item_history_entry, items));
        listView.setDivider(new ColorDrawable(ContextCompat.getColor(context, R.color.divider)));
        listView.setDividerHeight(Math.round(density));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(titleView);
        container.addView(listView);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(container)
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            listener.onSelected(position);
        });

        dialog.show();
    }
}
