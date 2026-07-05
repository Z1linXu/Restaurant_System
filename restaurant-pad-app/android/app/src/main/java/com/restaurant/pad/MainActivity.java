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
import android.util.Base64;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String APP_HOST = "restaurant-pad.local";
    private static final String APP_URL = "https://" + APP_HOST + "/index.html";
    private static final String PREFS = "restaurant_pad_settings";
    private static final String KEY_API_BASE = "api_base_url";
    private static final String KEY_WEB_APP_URL = "web_app_url";
    private static final String KEY_PRINTER_TEST_IP = "printer_test_ip";
    private static final String KEY_PRINTER_TEST_PORT = "printer_test_port";
    private static final String KEY_PRINTER_TEST_TIMEOUT_MS = "printer_test_timeout_ms";
    private static final String KEY_DEVICE_ID = "pad_direct_device_id";
    private static final String KEY_DEVICE_TOKEN = "pad_direct_device_token";
    private static final String KEY_DEVICE_STORE_ID = "pad_direct_store_id";
    private static final String KEY_DEVICE_NAME = "pad_direct_device_name";
    private static final String KEY_DEVICE_REGISTERED_AT = "pad_direct_registered_at";
    private static final String KEY_DEVICE_APP_VERSION = "pad_direct_app_version";
    private static final String KEY_DEVICE_PLATFORM = "pad_direct_platform";
    private static final int DEFAULT_PRINTER_TEST_PORT = 9100;
    private static final int DEFAULT_PRINTER_TEST_TIMEOUT_MS = 3000;

    private WebView webView;
    private SharedPreferences preferences;
    private PrinterPluginBridge printerPluginBridge;
    private volatile boolean padDirectJobInProgress = false;

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
        printerPluginBridge = new PrinterPluginBridge();
        webView.addJavascriptInterface(printerPluginBridge, "RestaurantPrinter");
        webView.addJavascriptInterface(new PadDeviceBridge(), "RestaurantPadDevice");

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
        addSectionText(layout, "Pad Direct Pairing", deviceStatusText());
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

        TextView printerHeading = new TextView(this);
        printerHeading.setText("Local Printer Test / 本地打印机测试");
        printerHeading.setTextSize(16);
        printerHeading.setPadding(0, padding, 0, 4);
        layout.addView(printerHeading);

        TextView printerNote = new TextView(this);
        printerNote.setText("Local LAN test only. This does not enable PAD_DIRECT worker or print real orders.");
        layout.addView(printerNote);

        TextView printerIpLabel = new TextView(this);
        printerIpLabel.setText("Printer IP");
        EditText printerIpInput = new EditText(this);
        printerIpInput.setSingleLine(true);
        printerIpInput.setHint("192.168.x.x");
        printerIpInput.setText(getPrinterTestIp());
        printerIpInput.setSelectAllOnFocus(true);

        TextView printerPortLabel = new TextView(this);
        printerPortLabel.setText("Port");
        EditText printerPortInput = new EditText(this);
        printerPortInput.setSingleLine(true);
        printerPortInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        printerPortInput.setHint(String.valueOf(DEFAULT_PRINTER_TEST_PORT));
        printerPortInput.setText(String.valueOf(getPrinterTestPort()));
        printerPortInput.setSelectAllOnFocus(true);

        TextView printerTimeoutLabel = new TextView(this);
        printerTimeoutLabel.setText("Timeout ms");
        EditText printerTimeoutInput = new EditText(this);
        printerTimeoutInput.setSingleLine(true);
        printerTimeoutInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        printerTimeoutInput.setHint(String.valueOf(DEFAULT_PRINTER_TEST_TIMEOUT_MS));
        printerTimeoutInput.setText(String.valueOf(getPrinterTestTimeoutMs()));
        printerTimeoutInput.setSelectAllOnFocus(true);

        TextView printerResult = new TextView(this);
        printerResult.setText("Enter printer IP, then test connection before test print.");
        printerResult.setTextIsSelectable(true);
        printerResult.setPadding(0, 8, 0, 8);

        layout.addView(printerIpLabel);
        layout.addView(printerIpInput);
        layout.addView(printerPortLabel);
        layout.addView(printerPortInput);
        layout.addView(printerTimeoutLabel);
        layout.addView(printerTimeoutInput);
        layout.addView(printerResult);

        Button testConnectionButton = addPanelButton(layout, "Test Printer Connection", () -> {});
        Button testPrintButton = addPanelButton(layout, "Test Print", () -> {});
        testConnectionButton.setOnClickListener(view -> runPrinterTest(
            false,
            printerIpInput,
            printerPortInput,
            printerTimeoutInput,
            printerResult,
            testConnectionButton,
            testPrintButton
        ));
        testPrintButton.setOnClickListener(view -> runPrinterTest(
            true,
            printerIpInput,
            printerPortInput,
            printerTimeoutInput,
            printerResult,
            testConnectionButton,
            testPrintButton
        ));

        TextView pendingHeading = new TextView(this);
        pendingHeading.setText("PAD_DIRECT Pending Print Jobs / 待打印任务");
        pendingHeading.setTextSize(16);
        pendingHeading.setPadding(0, padding, 0, 4);
        layout.addView(pendingHeading);

        TextView pendingNote = new TextView(this);
        pendingNote.setText("Manual mode only. One button processes one job: claim -> payload -> local TCP print -> complete/fail. No worker or auto polling.");
        layout.addView(pendingNote);

        TextView pendingResult = new TextView(this);
        pendingResult.setText(isDevicePaired()
            ? "点击刷新待打印任务。需要门店打印模式为 PAD_DIRECT。"
            : "请先在 Web 打印中心配对本机 Pad。");
        pendingResult.setTextIsSelectable(true);
        pendingResult.setPadding(0, 8, 0, 8);
        layout.addView(pendingResult);

        LinearLayout pendingJobsList = new LinearLayout(this);
        pendingJobsList.setOrientation(LinearLayout.VERTICAL);
        layout.addView(pendingJobsList);

        Button refreshPendingJobsButton = addPanelButton(layout, "Refresh Pending Print Jobs / 刷新待打印任务", () -> {});
        refreshPendingJobsButton.setEnabled(isDevicePaired());
        refreshPendingJobsButton.setOnClickListener(view -> refreshPendingPrintJobs(
            pendingResult,
            pendingJobsList,
            refreshPendingJobsButton,
            printerIpInput,
            printerPortInput,
            printerTimeoutInput
        ));

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
        addPanelButton(layout, "Clear Pairing / 清除配对", () -> confirmClearPairing(dialogRef));

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
                    .putString(KEY_PRINTER_TEST_IP, printerIpInput.getText().toString().trim())
                    .putString(KEY_PRINTER_TEST_PORT, String.valueOf(parsePort(printerPortInput.getText().toString())))
                    .putString(KEY_PRINTER_TEST_TIMEOUT_MS, String.valueOf(parseTimeoutMs(printerTimeoutInput.getText().toString())))
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

    private Button addPanelButton(LinearLayout layout, String label, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        layout.addView(button);
        return button;
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

    private String deviceStatusText() {
        String deviceId = preferences.getString(KEY_DEVICE_ID, "");
        if (deviceId == null || deviceId.isBlank()) {
            return "未配对。请在 Web 打印中心点击“配对本机 Pad”。";
        }
        String storeId = preferences.getString(KEY_DEVICE_STORE_ID, "");
        String deviceName = preferences.getString(KEY_DEVICE_NAME, "");
        String registeredAt = preferences.getString(KEY_DEVICE_REGISTERED_AT, "");
        String tokenLast4 = tokenLast4(preferences.getString(KEY_DEVICE_TOKEN, ""));
        return "已配对"
            + "\nDevice ID: " + deviceId
            + "\nStore ID: " + (storeId == null || storeId.isBlank() ? "-" : storeId)
            + "\nDevice name: " + (deviceName == null || deviceName.isBlank() ? "-" : deviceName)
            + "\nRegistered at: " + (registeredAt == null || registeredAt.isBlank() ? "-" : registeredAt)
            + "\nToken: " + (tokenLast4.isBlank() ? "已保存" : "****" + tokenLast4);
    }

    private boolean isDevicePaired() {
        String deviceId = preferences.getString(KEY_DEVICE_ID, "");
        String deviceToken = preferences.getString(KEY_DEVICE_TOKEN, "");
        String storeId = preferences.getString(KEY_DEVICE_STORE_ID, "");
        return deviceId != null && !deviceId.isBlank()
            && deviceToken != null && !deviceToken.isBlank()
            && storeId != null && !storeId.isBlank();
    }

    private void confirmClearPairing(AlertDialog[] dialogRef) {
        new AlertDialog.Builder(this)
            .setTitle("清除配对")
            .setMessage("确定要清除本机 Pad Direct device credentials 吗？清除后需要重新配对才能领取打印任务。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清除", (ignored, which) -> {
                clearDeviceCredentials();
                Toast.makeText(this, "本机配对已清除。", Toast.LENGTH_LONG).show();
                dismissDialog(dialogRef);
            })
            .show();
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

    private void refreshPendingPrintJobs(
        TextView resultView,
        LinearLayout jobsList,
        Button refreshButton,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput
    ) {
        String deviceId = preferences.getString(KEY_DEVICE_ID, "");
        String deviceToken = preferences.getString(KEY_DEVICE_TOKEN, "");
        String storeId = preferences.getString(KEY_DEVICE_STORE_ID, "");
        if (deviceId == null || deviceId.isBlank() || deviceToken == null || deviceToken.isBlank() || storeId == null || storeId.isBlank()) {
            resultView.setText("请先配对本机 Pad。");
            jobsList.removeAllViews();
            return;
        }
        String apiBase = pendingJobsApiBaseOrigin();
        if (apiBase.isBlank()) {
            resultView.setText("无法推导后端 API 地址。Local Preview 请设置 Web App URL；Bundled Assets 请设置 API Base URL。");
            jobsList.removeAllViews();
            return;
        }

        refreshButton.setEnabled(false);
        jobsList.removeAllViews();
        resultView.setText("正在刷新待打印任务...");
        new Thread(() -> {
            String message;
            String successBody = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(trimTrailingSlash(apiBase) + "/api/v1/stores/" + storeId + "/printing/jobs/pending?limit=25");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("X-Device-Id", deviceId);
                connection.setRequestProperty("X-Device-Token", deviceToken);
                int status = connection.getResponseCode();
                String body = readConnectionBody(connection, status);
                if (status == 401 || status == 403) {
                    message = "设备认证失败，请重新配对。\nHTTP " + status;
                } else if (status < 200 || status >= 300) {
                    message = "待打印任务加载失败。\nHTTP " + status + "\n" + body;
                } else {
                    message = "待打印任务已刷新。";
                    successBody = body;
                }
            } catch (Exception exception) {
                message = "无法连接后端，请检查 Web App URL / WiFi / preview:lan / backend。\n技术信息: "
                    + exception.getClass().getSimpleName() + " - " + exception.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            String finalMessage = message;
            String finalSuccessBody = successBody;
            runOnUiThread(() -> {
                if (finalSuccessBody == null) {
                    resultView.setText(finalMessage);
                    jobsList.removeAllViews();
                } else {
                    populatePendingJobs(
                        finalSuccessBody,
                        resultView,
                        jobsList,
                        refreshButton,
                        printerIpInput,
                        printerPortInput,
                        printerTimeoutInput
                    );
                }
                refreshButton.setEnabled(isDevicePaired());
            });
        }).start();
    }

    private String pendingJobsApiBaseOrigin() {
        String webAppUrl = getWebAppUrl();
        if (!webAppUrl.isBlank()) {
            String origin = originOf(webAppUrl);
            if (!origin.isBlank()) {
                return origin;
            }
        }
        String apiBaseUrl = getApiBaseUrl();
        if (!apiBaseUrl.isBlank()) {
            String origin = originOf(apiBaseUrl);
            if (!origin.isBlank()) {
                return origin;
            }
        }
        String currentOrigin = originOf(webView == null ? null : webView.getUrl());
        return currentOrigin.contains(APP_HOST) ? "" : currentOrigin;
    }

    private String readConnectionBody(HttpURLConnection connection, int status) throws Exception {
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append('\n');
            }
        }
        return builder.toString().trim();
    }

    private void populatePendingJobs(
        String body,
        TextView resultView,
        LinearLayout jobsList,
        Button refreshButton,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput
    ) {
        jobsList.removeAllViews();
        try {
            JSONObject response = new JSONObject(body);
            if (!response.optBoolean("success", false)) {
                resultView.setText(response.optString("message", "待打印任务加载失败。"));
                return;
            }
            JSONArray jobs = response.optJSONArray("data");
            if (jobs == null || jobs.length() == 0) {
                resultView.setText("暂无待打印任务。");
                return;
            }
            resultView.setText("待打印任务 " + jobs.length() + " 个。点击单个任务手动领取并打印。");
            for (int index = 0; index < jobs.length(); index++) {
                JSONObject job = jobs.getJSONObject(index);
                addPendingJobCard(
                    jobsList,
                    job,
                    resultView,
                    refreshButton,
                    printerIpInput,
                    printerPortInput,
                    printerTimeoutInput
                );
            }
        } catch (Exception exception) {
            resultView.setText("待打印任务解析失败: " + exception.getMessage());
        }
    }

    private void addPendingJobCard(
        LinearLayout jobsList,
        JSONObject job,
        TextView resultView,
        Button refreshButton,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput
    ) {
        float density = getResources().getDisplayMetrics().density;
        int padding = Math.round(10 * density);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(padding, padding, padding, padding);

        TextView details = new TextView(this);
        details.setText(formatPendingJobDetails(job));
        details.setTextIsSelectable(true);
        card.addView(details);

        Button printButton = new Button(this);
        printButton.setAllCaps(false);
        printButton.setText("领取并打印");
        String jobJson = job.toString();
        printButton.setOnClickListener(view -> {
            try {
                JSONObject selectedJob = new JSONObject(jobJson);
                processPendingPrintJob(
                    selectedJob,
                    resultView,
                    jobsList,
                    refreshButton,
                    printButton,
                    printerIpInput,
                    printerPortInput,
                    printerTimeoutInput
                );
            } catch (Exception exception) {
                resultView.setText("任务读取失败: " + exception.getMessage());
            }
        });
        card.addView(printButton);
        jobsList.addView(card);
    }

    private String formatPendingJobDetails(JSONObject job) {
        StringBuilder builder = new StringBuilder();
        builder.append("Job #").append(job.optString("id", "-"));
        builder.append(" | ").append(job.optString("module_code", "-"));
        builder.append(" | ").append(job.optString("status", "-"));
        builder.append('\n');
        builder.append("Order: ").append(job.optString("order_id", "-"));
        String createdAt = job.optString("created_at", "");
        if (!createdAt.isBlank() && !"null".equalsIgnoreCase(createdAt)) {
            builder.append(" | Created: ").append(createdAt);
        }
        builder.append('\n');
        String endpoint = job.optString("printer_endpoint", "");
        if (!endpoint.isBlank() && !"null".equalsIgnoreCase(endpoint)) {
            builder.append("Printer: ").append(endpoint).append('\n');
        }
        String claimedBy = job.optString("claimed_by_device_id", "");
        if (!claimedBy.isBlank() && !"null".equalsIgnoreCase(claimedBy)) {
            builder.append("Claimed by device: ").append(claimedBy);
            String expiresAt = job.optString("claim_expires_at", "");
            if (!expiresAt.isBlank() && !"null".equalsIgnoreCase(expiresAt)) {
                builder.append(" until ").append(expiresAt);
            }
            builder.append('\n');
        }
        String operatorMessage = job.optString("operator_message", "");
        String errorMessage = job.optString("error_message", "");
        if (!operatorMessage.isBlank() && !"null".equalsIgnoreCase(operatorMessage)) {
            builder.append("Message: ").append(operatorMessage).append('\n');
        } else if (!errorMessage.isBlank() && !"null".equalsIgnoreCase(errorMessage)) {
            builder.append("Message: ").append(errorMessage).append('\n');
        }
        return builder.toString().trim();
    }

    private void processPendingPrintJob(
        JSONObject job,
        TextView resultView,
        LinearLayout jobsList,
        Button refreshButton,
        Button printButton,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput
    ) {
        if (padDirectJobInProgress) {
            resultView.setText("已有任务正在处理，请等待当前任务完成。");
            return;
        }
        String deviceId = preferences.getString(KEY_DEVICE_ID, "");
        String deviceToken = preferences.getString(KEY_DEVICE_TOKEN, "");
        String storeId = preferences.getString(KEY_DEVICE_STORE_ID, "");
        if (deviceId == null || deviceId.isBlank() || deviceToken == null || deviceToken.isBlank() || storeId == null || storeId.isBlank()) {
            resultView.setText("请先配对本机 Pad。");
            return;
        }
        String printerIp = printerIpInput.getText().toString().trim();
        if (printerIp.isBlank()) {
            resultView.setText("请先配置本机打印机 IP。");
            return;
        }
        int printerPort = parsePort(printerPortInput.getText().toString());
        int printerTimeoutMs = parseTimeoutMs(printerTimeoutInput.getText().toString());
        printerPortInput.setText(String.valueOf(printerPort));
        printerTimeoutInput.setText(String.valueOf(printerTimeoutMs));
        preferences.edit()
            .putString(KEY_PRINTER_TEST_IP, printerIp)
            .putString(KEY_PRINTER_TEST_PORT, String.valueOf(printerPort))
            .putString(KEY_PRINTER_TEST_TIMEOUT_MS, String.valueOf(printerTimeoutMs))
            .apply();

        long jobId = job.optLong("id", -1L);
        if (jobId <= 0) {
            resultView.setText("任务缺少有效 job id。");
            return;
        }
        String moduleCode = job.optString("module_code", "PAD_DIRECT");
        String attemptToken = "android-" + deviceId + "-" + jobId + "-" + System.currentTimeMillis();

        padDirectJobInProgress = true;
        printButton.setEnabled(false);
        refreshButton.setEnabled(false);
        resultView.setText("正在领取并打印 Job #" + jobId + " (" + moduleCode + ")...");

        new Thread(() -> {
            String message;
            boolean claimed = false;
            boolean localPrintSent = false;
            boolean refreshAfter = false;
            try {
                JSONObject claimRequest = new JSONObject();
                claimRequest.put("client_attempt_token", attemptToken);
                claimRequest.put("lease_seconds", 300);
                HttpResult claimResult = postDeviceJson("/api/v1/printing/jobs/" + jobId + "/claim", claimRequest);
                if (claimResult.status == 401 || claimResult.status == 403) {
                    message = "设备认证失败，请重新配对。";
                    postUiAfterPadDirectJob(resultView, refreshButton, printButton, message, false, jobsList, printerIpInput, printerPortInput, printerTimeoutInput);
                    return;
                }
                if (claimResult.status == 409) {
                    message = "任务已被其他 Pad 领取。";
                    postUiAfterPadDirectJob(resultView, refreshButton, printButton, message, true, jobsList, printerIpInput, printerPortInput, printerTimeoutInput);
                    return;
                }
                requireSuccessResponse(claimResult, "领取任务失败");
                claimed = true;

                HttpResult payloadResult = getDeviceJson("/api/v1/printing/jobs/" + jobId + "/payload");
                requireSuccessResponse(payloadResult, "获取打印 payload 失败");
                JSONObject payloadData = responseData(payloadResult.body);
                String payloadBase64 = payloadData.optString("escpos_payload_base64", "").trim();
                if (payloadBase64.isBlank()) {
                    throw new PadDirectStepException("ANDROID_PAYLOAD_MISSING", "打印 payload 缺失", payloadResult.body);
                }
                try {
                    Base64.decode(payloadBase64, Base64.DEFAULT);
                } catch (Exception exception) {
                    throw new PadDirectStepException("ANDROID_PAYLOAD_INVALID", "打印 payload 不是有效 base64", exception.getMessage());
                }

                JSONObject printRequest = new JSONObject();
                printRequest.put("ip", printerIp);
                printRequest.put("port", printerPort);
                printRequest.put("timeoutMs", printerTimeoutMs);
                printRequest.put("payloadBase64", payloadBase64);
                String rawPrintResult = printerPluginBridge.printRawTcp(printRequest.toString());
                JSONObject printResult = new JSONObject(rawPrintResult);
                if (!printResult.optBoolean("success", false)) {
                    String nativeCode = printResult.optString("error_code", "ANDROID_NATIVE_PRINT_FAILED");
                    String nativeMessage = printResult.optString("message", "");
                    throw new PadDirectStepException(
                        "ANDROID_NATIVE_PRINT_FAILED",
                        "本机打印失败，检查打印机 IP/WiFi/纸张。" + (nativeMessage.isBlank() ? "" : " 技术信息: " + nativeCode + " - " + nativeMessage),
                        rawPrintResult
                    );
                }
                localPrintSent = true;

                JSONObject completeRequest = new JSONObject();
                completeRequest.put("client_attempt_token", attemptToken);
                completeRequest.put("raw_result", rawPrintResult);
                HttpResult completeResult = postDeviceJson("/api/v1/printing/jobs/" + jobId + "/complete", completeRequest);
                requireSuccessResponse(completeResult, "本地已打印，但回报后端完成失败。请检查 Print Center，避免重复打印");
                message = "打印完成：Job #" + jobId + " (" + moduleCode + ")";
                refreshAfter = true;
            } catch (PadDirectStepException exception) {
                message = exception.getMessage();
                if (claimed && !localPrintSent) {
                    message = message + "\n" + reportPadDirectFail(jobId, attemptToken, exception.errorCode, exception.getMessage(), exception.rawResult);
                    refreshAfter = true;
                } else if (localPrintSent) {
                    refreshAfter = true;
                }
            } catch (Exception exception) {
                message = "无法连接后端或处理任务失败: " + exception.getClass().getSimpleName() + " - " + exception.getMessage();
                if (claimed && !localPrintSent) {
                    message = message + "\n" + reportPadDirectFail(jobId, attemptToken, "ANDROID_NETWORK_ERROR", message, exception.getMessage());
                    refreshAfter = true;
                } else if (localPrintSent) {
                    refreshAfter = true;
                }
            }
            postUiAfterPadDirectJob(resultView, refreshButton, printButton, message, refreshAfter, jobsList, printerIpInput, printerPortInput, printerTimeoutInput);
        }).start();
    }

    private void postUiAfterPadDirectJob(
        TextView resultView,
        Button refreshButton,
        Button printButton,
        String message,
        boolean refreshAfter,
        LinearLayout jobsList,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput
    ) {
        runOnUiThread(() -> {
            resultView.setText(message);
            padDirectJobInProgress = false;
            refreshButton.setEnabled(isDevicePaired());
            printButton.setEnabled(!refreshAfter && isDevicePaired());
            if (refreshAfter) {
                refreshPendingPrintJobs(resultView, jobsList, refreshButton, printerIpInput, printerPortInput, printerTimeoutInput);
            }
        });
    }

    private String reportPadDirectFail(Long jobId, String attemptToken, String errorCode, String errorMessage, String rawResult) {
        try {
            JSONObject failRequest = new JSONObject();
            failRequest.put("client_attempt_token", attemptToken);
            failRequest.put("error_code", errorCode == null || errorCode.isBlank() ? "ANDROID_NATIVE_PRINT_FAILED" : errorCode);
            failRequest.put("error_message", truncateForJson(errorMessage, 1000));
            failRequest.put("raw_result", truncateForJson(rawResult, 3000));
            HttpResult failResult = postDeviceJson("/api/v1/printing/jobs/" + jobId + "/fail", failRequest);
            if (failResult.status >= 200 && failResult.status < 300) {
                return "打印失败，已回报后端。";
            }
            return "打印失败，但回报后端失败也失败。HTTP " + failResult.status;
        } catch (Exception exception) {
            return "打印失败，但回报后端失败也失败: " + exception.getMessage();
        }
    }

    private HttpResult getDeviceJson(String path) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = openDeviceConnection(path, "GET");
            int status = connection.getResponseCode();
            return new HttpResult(status, readConnectionBody(connection, status));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpResult postDeviceJson(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = openDeviceConnection(path, "POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload);
            }
            int status = connection.getResponseCode();
            return new HttpResult(status, readConnectionBody(connection, status));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection openDeviceConnection(String path, String method) throws Exception {
        String apiBase = pendingJobsApiBaseOrigin();
        if (apiBase.isBlank()) {
            throw new IllegalStateException("无法推导后端 API 地址。");
        }
        URL url = new URL(trimTrailingSlash(apiBase) + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Device-Id", preferences.getString(KEY_DEVICE_ID, ""));
        connection.setRequestProperty("X-Device-Token", preferences.getString(KEY_DEVICE_TOKEN, ""));
        return connection;
    }

    private void requireSuccessResponse(HttpResult result, String fallbackMessage) throws Exception {
        if (result.status == 401 || result.status == 403) {
            throw new PadDirectStepException("ANDROID_DEVICE_AUTH_FAILED", "设备认证失败，请重新配对。", result.body);
        }
        if (result.status == 409) {
            throw new PadDirectStepException("ANDROID_JOB_CONFLICT", "任务已被其他 Pad 领取。", result.body);
        }
        if (result.status < 200 || result.status >= 300) {
            throw new PadDirectStepException("ANDROID_NETWORK_ERROR", fallbackMessage + " HTTP " + result.status, result.body);
        }
        JSONObject response = new JSONObject(result.body);
        if (!response.optBoolean("success", false)) {
            throw new PadDirectStepException("ANDROID_API_ERROR", response.optString("message", fallbackMessage), result.body);
        }
    }

    private JSONObject responseData(String body) throws Exception {
        JSONObject response = new JSONObject(body);
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            throw new PadDirectStepException("ANDROID_PAYLOAD_MISSING", "后端响应缺少 data。", body);
        }
        return data;
    }

    private String truncateForJson(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String getPrinterTestIp() {
        return preferences.getString(KEY_PRINTER_TEST_IP, "");
    }

    private int getPrinterTestPort() {
        return parsePort(preferences.getString(KEY_PRINTER_TEST_PORT, String.valueOf(DEFAULT_PRINTER_TEST_PORT)));
    }

    private int getPrinterTestTimeoutMs() {
        return parseTimeoutMs(preferences.getString(KEY_PRINTER_TEST_TIMEOUT_MS, String.valueOf(DEFAULT_PRINTER_TEST_TIMEOUT_MS)));
    }

    private int parsePort(String rawValue) {
        int parsed = parseIntOrDefault(rawValue, DEFAULT_PRINTER_TEST_PORT);
        if (parsed <= 0 || parsed > 65535) {
            return DEFAULT_PRINTER_TEST_PORT;
        }
        return parsed;
    }

    private int parseTimeoutMs(String rawValue) {
        int parsed = parseIntOrDefault(rawValue, DEFAULT_PRINTER_TEST_TIMEOUT_MS);
        if (parsed < 500) {
            return 500;
        }
        return Math.min(parsed, 30000);
    }

    private int parseIntOrDefault(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private void runPrinterTest(
        boolean printTest,
        EditText ipInput,
        EditText portInput,
        EditText timeoutInput,
        TextView resultView,
        Button testConnectionButton,
        Button testPrintButton
    ) {
        String ip = ipInput.getText().toString().trim();
        int port = parsePort(portInput.getText().toString());
        int timeoutMs = parseTimeoutMs(timeoutInput.getText().toString());
        portInput.setText(String.valueOf(port));
        timeoutInput.setText(String.valueOf(timeoutMs));

        if (ip.isBlank()) {
            resultView.setText("请输入打印机 IP。");
            return;
        }

        preferences.edit()
            .putString(KEY_PRINTER_TEST_IP, ip)
            .putString(KEY_PRINTER_TEST_PORT, String.valueOf(port))
            .putString(KEY_PRINTER_TEST_TIMEOUT_MS, String.valueOf(timeoutMs))
            .apply();

        testConnectionButton.setEnabled(false);
        testPrintButton.setEnabled(false);
        resultView.setText(printTest ? "正在发送测试票..." : "正在测试连接...");

        new Thread(() -> {
            String message;
            try {
                JSONObject request = new JSONObject();
                request.put("ip", ip);
                request.put("port", port);
                request.put("timeoutMs", timeoutMs);
                String rawResult;
                if (printTest) {
                    request.put("payloadBase64", buildPrinterTestPayloadBase64(ip, port));
                    rawResult = printerPluginBridge.printRawTcp(request.toString());
                } else {
                    rawResult = printerPluginBridge.testConnection(request.toString());
                }
                message = formatPrinterResult(rawResult, printTest);
            } catch (Exception exception) {
                message = "测试失败: " + exception.getClass().getSimpleName() + " - " + exception.getMessage();
            }

            String finalMessage = message;
            runOnUiThread(() -> {
                resultView.setText(finalMessage);
                testConnectionButton.setEnabled(true);
                testPrintButton.setEnabled(true);
            });
        }).start();
    }

    private String formatPrinterResult(String rawResult, boolean printTest) {
        try {
            JSONObject result = new JSONObject(rawResult);
            if (result.optBoolean("success", false)) {
                return printTest ? "测试票已发送。请检查打印机出纸和切纸。" : "连接成功。";
            }
            String code = result.optString("error_code", "UNKNOWN");
            String message = result.optString("message", "");
            return printerErrorMessage(code) + "\n技术信息: " + code + (message.isBlank() ? "" : " - " + message);
        } catch (Exception exception) {
            return "无法读取测试结果: " + rawResult;
        }
    }

    private String printerErrorMessage(String code) {
        if ("TIMEOUT".equals(code)) {
            return "连接超时。请确认打印机开机、IP 正确、Pad 和打印机在同一 Wi-Fi/LAN。";
        }
        if ("CONNECTION_REFUSED".equals(code)) {
            return "连接被拒绝。请确认端口通常为 9100，且打印机支持 TCP 打印。";
        }
        if ("UNREACHABLE".equals(code)) {
            return "网络不可达。请检查子网、路由器 AP isolation/VLAN、打印机固定 IP。";
        }
        if ("WRITE_FAILED".equals(code)) {
            return "连接后写入失败。请检查打印机状态、纸张、网络稳定性。";
        }
        return "打印机测试失败。请检查 IP、端口、电源和网络。";
    }

    private String buildPrinterTestPayloadBase64(String ip, int port) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Charset charset = Charset.forName("GBK");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        outputStream.write(new byte[] {0x1B, 0x40});
        outputStream.write(new byte[] {0x1B, 0x45, 0x01});
        writePrinterLine(outputStream, "RESTAURANT PAD TEST", charset);
        outputStream.write(new byte[] {0x1B, 0x45, 0x00});
        writePrinterLine(outputStream, "打印机测试", charset);
        writePrinterLine(outputStream, "IP: " + ip + ":" + port, charset);
        writePrinterLine(outputStream, "Time: " + timestamp, charset);
        writePrinterLine(outputStream, "----------------", charset);
        outputStream.write('\n');
        outputStream.write('\n');
        outputStream.write(new byte[] {0x1D, 0x56, 0x41, 0x10});
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private void writePrinterLine(ByteArrayOutputStream outputStream, String line, Charset charset) throws Exception {
        outputStream.write(line.getBytes(charset));
        outputStream.write('\n');
    }

    private void clearDeviceCredentials() {
        preferences.edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_DEVICE_STORE_ID)
            .remove(KEY_DEVICE_NAME)
            .remove(KEY_DEVICE_REGISTERED_AT)
            .remove(KEY_DEVICE_APP_VERSION)
            .remove(KEY_DEVICE_PLATFORM)
            .apply();
    }

    private String tokenLast4(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        return token.length() <= 4 ? token : token.substring(token.length() - 4);
    }

    private String nowTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private String jsonSuccess(JSONObject extra) {
        try {
            JSONObject response = extra == null ? new JSONObject() : extra;
            response.put("success", true);
            return response.toString();
        } catch (Exception ignored) {
            return "{\"success\":true}";
        }
    }

    private String jsonFailure(String message) {
        try {
            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("message", message);
            return response.toString();
        } catch (Exception ignored) {
            return "{\"success\":false,\"message\":\"Unknown error\"}";
        }
    }

    private static class HttpResult {
        private final int status;
        private final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }

    private static class PadDirectStepException extends Exception {
        private final String errorCode;
        private final String rawResult;

        private PadDirectStepException(String errorCode, String message, String rawResult) {
            super(message);
            this.errorCode = errorCode == null || errorCode.isBlank() ? "ANDROID_NATIVE_PRINT_FAILED" : errorCode;
            this.rawResult = rawResult == null ? "" : rawResult;
        }
    }

    private class PadDeviceBridge {
        @JavascriptInterface
        public String saveDeviceCredentials(String json) {
            try {
                JSONObject request = new JSONObject(json == null ? "{}" : json);
                String deviceId = request.optString("device_id", "").trim();
                String deviceToken = request.optString("device_token", "").trim();
                String storeId = request.optString("store_id", "").trim();
                if (deviceId.isEmpty() || deviceToken.isEmpty() || storeId.isEmpty()) {
                    return jsonFailure("device_id, store_id, and device_token are required");
                }
                // Local pilot storage only. Move this token to EncryptedSharedPreferences
                // or Android Keystore before production Pad Direct worker rollout.
                preferences.edit()
                    .putString(KEY_DEVICE_ID, deviceId)
                    .putString(KEY_DEVICE_TOKEN, deviceToken)
                    .putString(KEY_DEVICE_STORE_ID, storeId)
                    .putString(KEY_DEVICE_NAME, request.optString("device_name", "Restaurant Pad"))
                    .putString(KEY_DEVICE_REGISTERED_AT, request.optString("registered_at", nowTimestamp()))
                    .putString(KEY_DEVICE_APP_VERSION, request.optString("app_version", "unknown"))
                    .putString(KEY_DEVICE_PLATFORM, request.optString("platform", "ANDROID"))
                    .apply();
                JSONObject response = new JSONObject();
                response.put("message", "Device credentials saved");
                response.put("device_id", deviceId);
                response.put("store_id", storeId);
                response.put("token_last4", tokenLast4(deviceToken));
                return jsonSuccess(response);
            } catch (Exception exception) {
                return jsonFailure(exception.getMessage() == null ? "Failed to save device credentials" : exception.getMessage());
            }
        }

        @JavascriptInterface
        public String getDeviceStatus() {
            try {
                JSONObject response = new JSONObject();
                String deviceId = preferences.getString(KEY_DEVICE_ID, "");
                String deviceToken = preferences.getString(KEY_DEVICE_TOKEN, "");
                response.put("success", true);
                response.put("paired", deviceId != null && !deviceId.isBlank() && deviceToken != null && !deviceToken.isBlank());
                response.put("device_id", blankToJsonNull(deviceId));
                response.put("store_id", blankToJsonNull(preferences.getString(KEY_DEVICE_STORE_ID, "")));
                response.put("device_name", blankToJsonNull(preferences.getString(KEY_DEVICE_NAME, "")));
                response.put("registered_at", blankToJsonNull(preferences.getString(KEY_DEVICE_REGISTERED_AT, "")));
                response.put("app_version", blankToJsonNull(preferences.getString(KEY_DEVICE_APP_VERSION, "unknown")));
                response.put("platform", blankToJsonNull(preferences.getString(KEY_DEVICE_PLATFORM, "ANDROID")));
                response.put("token_last4", tokenLast4(deviceToken));
                return response.toString();
            } catch (Exception exception) {
                return jsonFailure(exception.getMessage() == null ? "Failed to read device status" : exception.getMessage());
            }
        }

        @JavascriptInterface
        public String clearDeviceCredentials() {
            MainActivity.this.clearDeviceCredentials();
            try {
                JSONObject response = new JSONObject();
                response.put("message", "Device credentials cleared");
                return jsonSuccess(response);
            } catch (Exception ignored) {
                return "{\"success\":true,\"message\":\"Device credentials cleared\"}";
            }
        }

        private Object blankToJsonNull(String value) {
            return value == null || value.isBlank() ? JSONObject.NULL : value;
        }
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
