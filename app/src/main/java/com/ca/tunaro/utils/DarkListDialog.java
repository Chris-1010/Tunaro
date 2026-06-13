package com.ca.tunaro.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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
 * Dark rounded dialog presenting a tappable list of items
 * (Developer screen command history, SongView variant picker).
 */
public class DarkListDialog {

    public interface OnEntrySelected {
        void onSelected(int position);
    }

    public static void show(Context context, String title, List<String> items, OnEntrySelected listener) {
        float density = context.getResources().getDisplayMetrics().density;

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(17);
        titleView.setTypeface(null, Typeface.BOLD);
        int pad = Math.round(18 * density);
        titleView.setPadding(pad, pad, pad, Math.round(8 * density));

        ListView listView = new ListView(context);
        listView.setAdapter(new ArrayAdapter<>(context, R.layout.item_history_entry, items));
        listView.setDivider(new ColorDrawable(0x33FFFFFF));
        listView.setDividerHeight(Math.round(density));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(titleView);
        container.addView(listView);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(container)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    ContextCompat.getDrawable(context, R.drawable.rounded_dialog_dark));
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            listener.onSelected(position);
        });

        dialog.show();
    }
}
