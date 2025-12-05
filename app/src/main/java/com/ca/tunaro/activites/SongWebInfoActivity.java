package com.ca.tunaro.activites;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.SelectedSongHolder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class SongWebInfoActivity extends BaseActivity {
    private static final String TAG = "SongWebInfoActivity";

    private WebView webView;
    private ProgressBar progressBar;
    private SongModel selectedSong;
    private boolean hasRedirected = false;

    private String moreDetailsUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_song_web_info);

        moreDetailsUrl = getString(R.string.more_details_url);

        // Get the selected song
        selectedSong = SelectedSongHolder.getInstance().getSelectedSong();

        if (selectedSong == null) {
            showToast("Error: No song selected");
            finish();
            return;
        }

        // Set up action bar with song name
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Web Info: " + selectedSong.getName());
        }

        // Initialize views
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.loading_progress);

        setupWebView();
        loadWebContent();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        //#region WebView Configuration
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        //#endregion

        //#region WebView Clients
        // Handle page loading progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        // Handle page loading events
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);

                // Check if redirected to homepage (invalid ISRC)
                if (!hasRedirected && url.equals(moreDetailsUrl)) {
                    Log.d(TAG, "ISRC not found, redirecting to Google search");

                    hasRedirected = true;

                    // Cancel loading songstats homepage
                    view.stopLoading();

                    // Load Google search instead
                    String searchQuery = selectedSong.getName() + " " + selectedSong.getArtist();
                    try {
                        //noinspection CharsetObjectCanBeUsed
                        String encodedQuery = URLEncoder.encode(searchQuery, "UTF-8");
                        String googleUrl = "https://www.google.com/search?q=" + encodedQuery;
                        view.loadUrl(googleUrl);

                        showToast("Song not found on Songstats. Searching Google...");
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Error encoding search query", e);
                    }
                } else if (url.equals(moreDetailsUrl)) {
                    // Exit activity when going back from a redirected page
                    finish();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                showToast("Error loading page: " + error.getDescription());
            }
        });
        //#endregion

        // Handle back button press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    private void loadWebContent() {
        String isrc = selectedSong.getIsrc();

        if (isrc == null || isrc.isEmpty()) {
            showToast("ISRC not available for this song");
            finish();
            return;
        }

        try {
            //noinspection CharsetObjectCanBeUsed
            String encodedQuery = URLEncoder.encode(isrc, "UTF-8");

            // Construct URL for Songstats
            String url = moreDetailsUrl + encodedQuery;

            Log.d(TAG, "Loading URL: " + url);
            webView.loadUrl(url);

        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Error encoding search query", e);
            showToast("Error creating search URL");
            finish();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}
