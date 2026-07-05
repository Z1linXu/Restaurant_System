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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

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
        final AlertDialog[] dialogRef = new AlertDialog[1];

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, 0, padding, 0);
        scrollView.addView(layout);

        addSectionText(layout, "Current WebView URL", currentWebViewUrl());
        addSectionText(layout, "Configured Web App URL", getWebAppUrl().isBlank() ? "(empty)" : getWebAppUrl());
        addSectionText(layout, "Mode", getWebAppUrl().isBlank() ? "Bundled Assets Mode" : "Local Preview Mode");
        addSectionText(layout, "Troubleshooting",
            "Android localhost is the Android device, not your computer.\n" +
            "Android and the computer must be on the same Wi-Fi.\n" +
            "Run npm run preview:lan for local preview.\n" +
            "Allow port 5173 through the computer firewall.");

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

        addPanelButton(layout, "Refresh Current Page", () -> {
            webView.reload();
            dismissDialog(dialogRef);
        });
        addShortcutButton(layout, "Open Frontdesk", "/frontdesk", "/frontdesk", dialogRef);
        addShortcutButton(layout, "Open Order Center", "/frontdesk/order", "/frontdesk/order", dialogRef);
        addShortcutButton(layout, "Open Print Center", "/admin/settings/printing", "/admin/settings/printing", dialogRef);
        addShortcutButton(layout, "Open Menu Management", "/admin/menu/items", "/admin/menu/items", dialogRef);
        addShortcutButton(layout, "Open Dining Tables", "/admin/settings/tables", "/admin/settings/tables", dialogRef);
        addPanelButton(layout, "Test Web App URL", () -> testWebAppUrl(webUrlInput.getText().toString().trim()));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Restaurant Pad Local Control Panel")
            .setMessage("Use this panel for local navigation shortcuts and LAN preview configuration. Business pages still run in the Web app.")
            .setView(scrollView)
            .setPositiveButton("Save", (ignored, which) -> {
                String webAppUrl = webUrlInput.getText().toString().trim();
                String apiBaseUrl = apiBaseInput.getText().toString().trim();
                preferences.edit()
                    .putString(KEY_WEB_APP_URL, webAppUrl)
                    .putString(KEY_API_BASE, apiBaseUrl)
                    .apply();
                loadApp();
            })
            .setNegativeButton(firstLaunch ? "Load Bundled Assets" : "Cancel", (ignored, which) -> {
                if (firstLaunch) {
                    loadApp();
                }
            })
            .create();
        dialogRef[0] = dialog;
        dialog.show();
    }

    private void addSectionText(LinearLayout layout, String label, String value) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(13);
        title.setPadding(0, 14, 0, 2);
        TextView body = new TextView(this);
        body.setText(value);
        body.setTextIsSelectable(true);
        layout.addView(title);
        layout.addView(body);
    }

    private void addPanelButton(LinearLayout layout, String label, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        layout.addView(button);
    }

    private void addShortcutButton(LinearLayout layout, String label, String storeScopedPath, String legacyPath, AlertDialog[] dialogRef) {
        addPanelButton(layout, label, () -> {
            webView.loadUrl(buildShortcutUrl(storeScopedPath, legacyPath));
            dismissDialog(dialogRef);
        });
    }

    private void dismissDialog(AlertDialog[] dialogRef) {
        if (dialogRef[0] != null) {
            dialogRef[0].dismiss();
        }
    }

    private String currentWebViewUrl() {
        String current = webView == null ? "" : webView.getUrl();
        return current == null || current.isBlank() ? "(not loaded yet)" : current;
    }

    private String buildShortcutUrl(String storeScopedPath, String legacyPath) {
        Integer storeId = resolveCurrentStoreId();
        String path = storeId == null ? legacyPath : "/stores/" + storeId + storeScopedPath;
        return navigationBaseOrigin() + path;
    }

    private Integer resolveCurrentStoreId() {
        Integer fromCurrentUrl = parseStoreId(webView == null ? null : webView.getUrl());
        if (fromCurrentUrl != null) {
            return fromCurrentUrl;
        }
        return parseStoreId(getWebAppUrl());
    }

    private Integer parseStoreId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Uri uri = Uri.parse(url);
        List<String> segments = uri.getPathSegments();
        if (segments.size() >= 2 && "stores".equals(segments.get(0))) {
            try {
                return Integer.valueOf(segments.get(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String navigationBaseOrigin() {
        String webAppUrl = getWebAppUrl();
        if (!webAppUrl.isBlank()) {
            String configuredOrigin = originOf(webAppUrl);
            if (!configuredOrigin.isBlank()) {
                return configuredOrigin;
            }
        }
        String currentOrigin = originOf(webView == null ? null : webView.getUrl());
        return currentOrigin.isBlank() ? originOf(APP_URL) : currentOrigin;
    }

    private String originOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        Uri uri = Uri.parse(url);
        if (uri.getScheme() == null || uri.getHost() == null) {
            return "";
        }
        StringBuilder origin = new StringBuilder();
        origin.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() > 0) {
            origin.append(":").append(uri.getPort());
        }
        return origin.toString();
    }

    private void testWebAppUrl(String rawUrl) {
        if (rawUrl.isBlank()) {
            Toast.makeText(this, "Set a Web App URL first.", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            String message;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(rawUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                int status = connection.getResponseCode();
                message = "Reachable. HTTP status: " + status;
            } catch (Exception exception) {
                message = "Failed: " + exception.getClass().getSimpleName() + " - " + exception.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            String finalMessage = message;
            runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Web App URL Test")
                .setMessage(finalMessage)
                .setPositiveButton("OK", null)
                .show());
        }).start();
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
