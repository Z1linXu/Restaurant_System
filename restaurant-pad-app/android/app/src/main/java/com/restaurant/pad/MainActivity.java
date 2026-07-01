package com.restaurant.pad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.EditText;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final String APP_HOST = "restaurant-pad.local";
    private static final String APP_URL = "https://" + APP_HOST + "/index.html";
    private static final String PREFS = "restaurant_pad_settings";
    private static final String KEY_API_BASE = "api_base_url";

    private WebView webView;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        webView = new WebView(this);
        setContentView(webView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        configureWebView();
        if (getApiBaseUrl().isBlank()) {
            showApiBaseDialog(true);
        } else {
            loadApp();
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new PrinterPluginBridge(), "RestaurantPrinter");

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
            .setDomain(APP_HOST)
            .addPathHandler("/", new FrontendAssetPathHandler(this, this::getApiBaseUrl))
            .build();

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (APP_HOST.equals(uri.getHost())) {
                    return false;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            }
        });

        webView.setOnLongClickListener(view -> {
            showApiBaseDialog(false);
            return true;
        });
    }

    private void loadApp() {
        webView.loadUrl(APP_URL);
    }

    private String getApiBaseUrl() {
        return preferences.getString(KEY_API_BASE, "");
    }

    private void showApiBaseDialog(boolean firstLaunch) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("http://your-lan-ip:8080");
        input.setText(getApiBaseUrl());
        input.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Restaurant API Base")
            .setMessage("Enter the backend base URL. Do not hardcode this in the app source.")
            .setView(input)
            .setPositiveButton("Save", (ignored, which) -> {
                String value = input.getText().toString().trim();
                preferences.edit().putString(KEY_API_BASE, value).apply();
                loadApp();
            })
            .setNegativeButton(firstLaunch ? "Load Offline Placeholder" : "Cancel", (ignored, which) -> loadApp())
            .create();
        dialog.show();
    }
}
