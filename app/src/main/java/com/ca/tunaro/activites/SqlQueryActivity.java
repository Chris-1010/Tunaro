package com.ca.tunaro.activites;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;

public class SqlQueryActivity extends BaseActivity {

    private EditText queryInput;
    private TextView resultsText;
    private TextView rowCount;
    private Button runButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkForRecovery()) return;
        setContentView(R.layout.activity_sql_query);

        queryInput = findViewById(R.id.query_input);
        resultsText = findViewById(R.id.results_text);
        rowCount = findViewById(R.id.row_count);
        runButton = findViewById(R.id.run_button);

        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        queryInput.setText("SELECT name, COUNT(*) as c FROM sqlite_master WHERE type='table' GROUP BY name");

        runButton.setOnClickListener(v -> runQuery());

        queryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) { runQuery(); return true; }
            return false;
        });
    }

    private void runQuery() {
        String sql = queryInput.getText().toString().trim();
        if (sql.isEmpty()) return;

        runButton.setEnabled(false);
        rowCount.setText("Running…");
        resultsText.setText("");

        android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            String resultText;
            String countText;
            DatabaseHelper dbHelper = new DatabaseHelper(this);
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
            runOnUiThread(() -> {
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
