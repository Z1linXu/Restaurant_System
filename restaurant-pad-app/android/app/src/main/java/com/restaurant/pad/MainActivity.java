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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    private static final int DEFAULT_PRINTER_TEST_PORT = 9100;
    private static final int DEFAULT_PRINTER_TEST_TIMEOUT_MS = 3000;

    private WebView webView;
    private SharedPreferences preferences;
    private PrinterPluginBridge printerPluginBridge;

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

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }
}
