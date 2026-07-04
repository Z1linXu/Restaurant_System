package com.restaurant.pad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final String APP_HOST = "restaurant-pad.local";
    private static final String APP_URL = "https://" + APP_HOST + "/index.html";
    private static final String PREFS = "restaurant_pad_settings";
    private static final String KEY_API_BASE = "api_base_url";
    private static final String KEY_WEB_APP_URL = "web_app_url";

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
        if (getWebAppUrl().isBlank() && getApiBaseUrl().isBlank()) {
            showRuntimeConfigDialog(true);
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
        if (isDebuggable()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
        }
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
                if (shouldOpenInApp(uri)) {
                    return false;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            }
        });

        webView.setOnLongClickListener(view -> {
            showRuntimeConfigDialog(false);
            return true;
        });
    }

    private void loadApp() {
        String webAppUrl = getWebAppUrl();
        webView.loadUrl(webAppUrl.isBlank() ? APP_URL : webAppUrl);
    }

    private String getApiBaseUrl() {
        return preferences.getString(KEY_API_BASE, "");
    }

    private String getWebAppUrl() {
        return preferences.getString(KEY_WEB_APP_URL, "");
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private boolean shouldOpenInApp(Uri uri) {
        if (uri == null) {
            return false;
        }
        if (APP_HOST.equals(uri.getHost())) {
            return true;
        }
        Uri configuredWebAppUri = Uri.parse(getWebAppUrl());
        return configuredWebAppUri != null
            && configuredWebAppUri.getHost() != null
            && configuredWebAppUri.getHost().equals(uri.getHost())
            && safePort(configuredWebAppUri) == safePort(uri)
            && safeScheme(configuredWebAppUri).equals(safeScheme(uri));
    }

    private int safePort(Uri uri) {
        int port = uri.getPort();
        if (port > 0) {
            return port;
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String safeScheme(Uri uri) {
        return uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
    }

    private void showRuntimeConfigDialog(boolean firstLaunch) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, 0, padding, 0);

        TextView webUrlLabel = new TextView(this);
        webUrlLabel.setText("Local Preview Web App URL (optional)");
        EditText webUrlInput = new EditText(this);
        webUrlInput.setSingleLine(true);
        webUrlInput.setHint("http://your-lan-ip:5173");
        webUrlInput.setText(getWebAppUrl());
        webUrlInput.setSelectAllOnFocus(true);

        TextView apiBaseLabel = new TextView(this);
        apiBaseLabel.setText("Bundled Assets API Base URL (optional)");
        EditText apiBaseInput = new EditText(this);
        apiBaseInput.setSingleLine(true);
        apiBaseInput.setHint("http://your-lan-ip:8080");
        apiBaseInput.setText(getApiBaseUrl());
        apiBaseInput.setSelectAllOnFocus(true);

        layout.addView(webUrlLabel);
        layout.addView(webUrlInput);
        layout.addView(apiBaseLabel);
        layout.addView(apiBaseInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Restaurant Pad Local Config")
            .setMessage("For LAN preview, set Web App URL to http://computer-lan-ip:5173. Leave it empty to use bundled assets with API Base URL.")
            .setView(layout)
            .setPositiveButton("Save", (ignored, which) -> {
                String webAppUrl = webUrlInput.getText().toString().trim();
                String apiBaseUrl = apiBaseInput.getText().toString().trim();
                preferences.edit()
                    .putString(KEY_WEB_APP_URL, webAppUrl)
                    .putString(KEY_API_BASE, apiBaseUrl)
                    .apply();
                loadApp();
            })
            .setNegativeButton(firstLaunch ? "Load Bundled Assets" : "Cancel", (ignored, which) -> loadApp())
            .create();
        dialog.show();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }
}
