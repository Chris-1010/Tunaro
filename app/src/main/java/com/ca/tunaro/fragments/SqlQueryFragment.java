package com.ca.tunaro.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.utils.DeveloperHistory;
import com.ca.tunaro.utils.DarkListDialog;

import java.util.List;

public class SqlQueryFragment extends Fragment {

    private static final String DEFAULT_QUERY =
            "SELECT name, COUNT(*) as c FROM sqlite_master WHERE type='table' GROUP BY name";

    private EditText queryInput;
    private TextView resultsText;
    private TextView rowCount;
    private Button runButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sql_query, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        queryInput = view.findViewById(R.id.query_input);
        resultsText = view.findViewById(R.id.results_text);
        rowCount = view.findViewById(R.id.row_count);
        runButton = view.findViewById(R.id.run_button);

        List<String> history = DeveloperHistory.getSqlQueries(requireContext());
        queryInput.setText(history.isEmpty() ? DEFAULT_QUERY : history.get(0));

        runButton.setOnClickListener(v -> runQuery());
        view.findViewById(R.id.history_button).setOnClickListener(v -> showHistory());

        queryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) { runQuery(); return true; }
            return false;
        });
    }

    private void showHistory() {
        List<String> history = DeveloperHistory.getSqlQueries(requireContext());
        if (history.isEmpty()) {
            Toast.makeText(requireContext(), "No query history yet", Toast.LENGTH_SHORT).show();
            return;
        }

        DarkListDialog.show(requireContext(), "Query History", history, position -> {
            String query = history.get(position);
            ClipboardManager clipboard =
                    (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("SQL query", query));
            queryInput.setText(query);
            queryInput.setSelection(query.length());
        });
    }

    private void runQuery() {
        String sql = queryInput.getText().toString().trim();
        if (sql.isEmpty()) return;

        DeveloperHistory.addSqlQuery(requireContext(), sql);

        runButton.setEnabled(false);
        rowCount.setText("Running…");
        resultsText.setText("");

        Context appContext = requireContext().getApplicationContext();
        android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            String resultText;
            String countText;
            DatabaseHelper dbHelper = new DatabaseHelper(appContext);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            try {
                String upper = sql.toUpperCase();
                if (upper.startsWith("SELECT") || upper.startsWith("PRAGMA") || upper.startsWith("EXPLAIN")) {
                    Cursor cursor = db.rawQuery(sql, null);
                    resultText = formatCursor(cursor);
                    countText = cursor.getCount() + " rows";
                    cursor.close();
                } else {
                    db.execSQL(sql);
                    resultText = "OK";
                    countText = "";
                }
            } catch (Exception e) {
                resultText = "Error: " + e.getMessage();
                countText = "";
            } finally {
                db.close();
                dbHelper.close();
            }
            String finalResult = resultText;
            String finalCount = countText;
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                resultsText.setText(finalResult);
                rowCount.setText(finalCount);
                runButton.setEnabled(true);
            });
        });
    }

    private String formatCursor(Cursor cursor) {
        if (cursor.getCount() == 0) return "(no rows)";

        String[] cols = cursor.getColumnNames();
        StringBuilder sb = new StringBuilder();

        for (String col : cols) {
            sb.append(col).append('\t');
        }
        sb.append('\n');
        for (int i = 0; i < cols.length * 8; i++) sb.append('-');
        sb.append('\n');

        while (cursor.moveToNext()) {
            for (int i = 0; i < cols.length; i++) {
                String val = cursor.getString(i);
                sb.append(val != null ? val : "NULL").append('\t');
            }
            sb.append('\n');
        }

        return sb.toString();
    }
}
