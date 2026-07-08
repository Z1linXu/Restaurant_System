package com.restaurant.pad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
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
    private static final String TAG = "RestaurantPad";
    private static final String WORKER_TAG = "RestaurantPadWorker";
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
    private static final String KEY_PAD_DIRECT_AUTO_ENABLED = "pad_direct_auto_enabled";
    private static final int DEFAULT_PRINTER_TEST_PORT = 9100;
    private static final int DEFAULT_PRINTER_TEST_TIMEOUT_MS = 3000;
    private static final long PAD_DIRECT_KICK_FIRST_DELAY_MS = 300;
    private static final long PAD_DIRECT_KICK_RETRY_DELAY_MS = 700;
    private static final int PAD_DIRECT_NATIVE_PRINT_MAX_ATTEMPTS = 3;
    private static final long[] PAD_DIRECT_CONNECT_RETRY_DELAYS_MS = new long[] {0, 500, 1500};
    private static final long[] PAD_DIRECT_RECOVERY_BACKOFF_MS = new long[] {2000, 5000, 10000, 30000};
    private static final long PAD_DIRECT_WATCHDOG_INTERVAL_MS = 5000;
    private static final long PAD_DIRECT_STALE_POLL_MS = 10000;
    private static final int PAD_DIRECT_MAX_CONSECUTIVE_ERRORS = 3;

    private WebView webView;
    private SharedPreferences preferences;
    private PrinterPluginBridge printerPluginBridge;
    private volatile boolean padDirectJobInProgress = false;
    private final Handler padDirectWorkerHandler = new Handler(Looper.getMainLooper());
    private final Runnable padDirectWorkerWatchdog = this::runPadDirectWorkerWatchdog;
    private volatile boolean padDirectWorkerRunning = false;
    private volatile boolean padDirectWorkerStopRequested = false;
    private volatile boolean padDirectWorkerInProgress = false;
    private volatile boolean padDirectWorkerPendingKick = false;
    private volatile int padDirectQuickPollsRemaining = 0;
    private volatile boolean padDirectWorkerStoppedForLifecycle = false;
    private volatile boolean padDirectWorkerUserStopped = false;
    private volatile boolean padDirectAppForeground = false;
    private volatile boolean padDirectWorkerPollScheduled = false;
    private volatile boolean padDirectWorkerWatchdogScheduled = false;
    private volatile boolean padDirectWorkerErrorStopped = false;
    private volatile long padDirectWorkerLastPollAtMs = 0L;
    private volatile long padDirectWorkerLastPollFinishedAtMs = 0L;
    private volatile long padDirectWorkerLastPollDurationMs = -1L;
    private volatile long padDirectWorkerLastOldestJobAgeMs = -1L;
    private volatile long padDirectWorkerLastJobQueueDelayMs = -1L;
    private volatile long padDirectWorkerLastJobProcessingDurationMs = -1L;
    private volatile long padDirectWorkerLastScheduledAtMs = 0L;
    private volatile long padDirectWorkerNextDelayMs = -1L;
    private volatile long padDirectWorkerLastRecoveryDelayMs = -1L;
    private volatile int padDirectWorkerLastPollResultCount = -1;
    private volatile int padDirectWorkerConsecutiveErrors = 0;
    private volatile int padDirectWorkerRecoveryAttempt = 0;
    private volatile String padDirectWorkerLastPollError = "";
    private volatile String padDirectWorkerLastStopReason = "";
    private volatile String padDirectWorkerLastStartReason = "";
    private volatile String padDirectWorkerCurrentJobId = "";
    private volatile String padDirectWorkerCurrentModule = "";
    private volatile String padDirectWorkerCurrentPrinterEndpoint = "";
    private volatile PadDirectWorkerState padDirectWorkerState = PadDirectWorkerState.STOPPED;
    private volatile PadDirectWorkerControls activePadDirectWorkerControls;

    private enum PadDirectWorkerState {
        STOPPED,
        STARTING,
        WAITING,
        POLLING,
        RECOVERING,
        PROCESSING_JOB,
        STOPPING,
        ERROR_STOPPED
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        padDirectAppForeground = true;
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
            startPadDirectWorkerHeadlessIfPaired("app-create");
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

        TextView controlPanelWarning = new TextView(this);
        controlPanelWarning.setText("");
        controlPanelWarning.setTextColor(0xFF9A3412);
        controlPanelWarning.setTextIsSelectable(true);
        controlPanelWarning.setPadding(0, 8, 0, 8);
        layout.addView(controlPanelWarning);

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

        addPanelButton(layout, "Save Settings / 保存配置", () -> saveRuntimeSettings(
            webUrlInput,
            apiBaseInput,
            printerIpInput,
            printerPortInput,
            printerTimeoutInput,
            dialogRef,
            true
        ));

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
        pendingNote.setText("Manual mode. One button processes one job: claim -> start-print -> payload -> assigned printer TCP print -> complete/fail. The Printer IP field above is only for local test prints.");
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

        TextView autoHeading = new TextView(this);
        autoHeading.setText("Semi-Auto PAD_DIRECT / 半自动处理打印任务");
        autoHeading.setTextSize(16);
        autoHeading.setPadding(0, padding, 0, 4);
        layout.addView(autoHeading);

        TextView autoNote = new TextView(this);
        autoNote.setText("Foreground only. Auto-start resumes only when auto processing is enabled. It processes one job at a time and stops on printer/backend/auth errors.");
        layout.addView(autoNote);

        TextView autoStatus = new TextView(this);
        autoStatus.setText(formatPadDirectWorkerStatus());
        autoStatus.setTextIsSelectable(true);
        autoStatus.setPadding(0, 8, 0, 8);
        layout.addView(autoStatus);

        Button startAutoButton = addPanelButton(layout, "Start Auto Print / 开启自动处理打印任务", () -> {});
        Button stopAutoButton = addPanelButton(layout, "Stop Auto Print / 停止自动处理", () -> {});
        startAutoButton.setEnabled(isDevicePaired() && !padDirectWorkerRunning);
        stopAutoButton.setEnabled(padDirectWorkerRunning);
        startAutoButton.setOnClickListener(view -> startPadDirectAutoWorker(
            autoStatus,
            pendingResult,
            pendingJobsList,
            refreshPendingJobsButton,
            startAutoButton,
            stopAutoButton,
            printerIpInput,
            printerPortInput,
            printerTimeoutInput,
            "manual/control-panel"
        ));
        stopAutoButton.setOnClickListener(view -> {
            padDirectWorkerUserStopped = true;
            setPadDirectAutoPrintEnabled(false);
            stopPadDirectAutoWorker(
                autoStatus,
                startAutoButton,
                stopAutoButton,
                "已停止。当前正在打印的任务会先完成/失败，不再领取新任务。",
                "user-disabled",
                false
            );
        });
        autoStartPadDirectWorkerIfNeeded(
            autoStatus,
            pendingResult,
            pendingJobsList,
            refreshPendingJobsButton,
            startAutoButton,
            stopAutoButton,
            printerIpInput,
            printerPortInput,
            printerTimeoutInput,
            controlPanelWarning
        );

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
                saveRuntimeSettings(webUrlInput, apiBaseInput, printerIpInput, printerPortInput, printerTimeoutInput, dialogRef, false);
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

    private void saveRuntimeSettings(
        EditText webUrlInput,
        EditText apiBaseInput,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput,
        AlertDialog[] dialogRef,
        boolean dismissAndReload
    ) {
        String webAppUrl = webUrlInput.getText().toString().trim();
        String apiBaseUrl = apiBaseInput.getText().toString().trim();
        String printerIp = printerIpInput.getText().toString().trim();
        int printerPort = parsePort(printerPortInput.getText().toString());
        int printerTimeoutMs = parseTimeoutMs(printerTimeoutInput.getText().toString());
        printerPortInput.setText(String.valueOf(printerPort));
        printerTimeoutInput.setText(String.valueOf(printerTimeoutMs));

        preferences.edit()
            .putString(KEY_WEB_APP_URL, webAppUrl)
            .putString(KEY_API_BASE, apiBaseUrl)
            .putString(KEY_PRINTER_TEST_IP, printerIp)
            .putString(KEY_PRINTER_TEST_PORT, String.valueOf(printerPort))
            .putString(KEY_PRINTER_TEST_TIMEOUT_MS, String.valueOf(printerTimeoutMs))
            .apply();

        Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show();
        if (dismissAndReload) {
            dismissDialog(dialogRef);
            loadApp();
        }
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

    private boolean isPadDirectAutoPrintEnabled() {
        // Default true preserves existing paired pilot behavior. Manual Stop writes false.
        return preferences.getBoolean(KEY_PAD_DIRECT_AUTO_ENABLED, true);
    }

    private void setPadDirectAutoPrintEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_PAD_DIRECT_AUTO_ENABLED, enabled).apply();
    }

    private void setPadDirectWorkerState(PadDirectWorkerState state) {
        padDirectWorkerState = state == null ? PadDirectWorkerState.STOPPED : state;
    }

    private void clearPadDirectCurrentJob() {
        padDirectWorkerCurrentJobId = "";
        padDirectWorkerCurrentModule = "";
        padDirectWorkerCurrentPrinterEndpoint = "";
    }

    private void clearPadDirectWorkerCallbacks() {
        padDirectWorkerHandler.removeCallbacksAndMessages(null);
        padDirectWorkerPollScheduled = false;
        padDirectWorkerWatchdogScheduled = false;
        padDirectWorkerNextDelayMs = -1L;
    }

    private String padDirectStateLabel(PadDirectWorkerState state) {
        if (state == null) {
            return "未知";
        }
        switch (state) {
            case STOPPED:
                return "已停止";
            case STARTING:
                return "正在启动";
            case WAITING:
                return "等待任务";
            case POLLING:
                return "正在轮询";
            case RECOVERING:
                return "网络异常，自动恢复中";
            case PROCESSING_JOB:
                return "正在处理任务";
            case STOPPING:
                return "正在停止";
            case ERROR_STOPPED:
                return "异常停止";
            default:
                return "未知";
        }
    }

    private String timestampOrDash(long timestampMs) {
        if (timestampMs <= 0) {
            return "-";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(timestampMs));
    }

    private String durationOrDash(long durationMs) {
        if (durationMs < 0) {
            return "-";
        }
        if (durationMs < 1000) {
            return durationMs + "ms";
        }
        long seconds = durationMs / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return minutes + "m " + remainingSeconds + "s";
    }

    private long parseTimestampMs(String value) {
        if (value == null || value.isBlank()) {
            return -1L;
        }
        String normalized = value.trim();
        String[] patterns = new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                return new SimpleDateFormat(pattern, Locale.US).parse(normalized).getTime();
            } catch (Exception ignored) {
                // Try the next known backend timestamp shape.
            }
        }
        return -1L;
    }

    private long ageMsFromTimestamp(String value, long nowMs) {
        long parsed = parseTimestampMs(value);
        if (parsed <= 0) {
            return -1L;
        }
        return Math.max(0L, nowMs - parsed);
    }

    private void updatePadDirectKeepScreenOn() {
        Runnable update = () -> {
            if (isPadDirectAutoPrintEnabled() && padDirectAppForeground && padDirectWorkerRunning && !padDirectWorkerStopRequested) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update.run();
        } else {
            runOnUiThread(update);
        }
    }

    private long logWorkerDuration(String metric, long jobId, long startedAtMs) {
        long durationMs = Math.max(0L, System.currentTimeMillis() - startedAtMs);
        Log.i(WORKER_TAG, metric + " jobId=" + jobId + " durationMs=" + durationMs);
        return durationMs;
    }

    private boolean isPadDirectWorkerPollStale(long nowMs) {
        if (!padDirectWorkerRunning || padDirectWorkerStopRequested || padDirectWorkerInProgress || padDirectJobInProgress) {
            return false;
        }
        if (padDirectWorkerState == PadDirectWorkerState.RECOVERING) {
            return false;
        }
        return padDirectWorkerLastPollAtMs <= 0 || nowMs - padDirectWorkerLastPollAtMs > PAD_DIRECT_STALE_POLL_MS;
    }

    private long nextPadDirectRecoveryDelayMs() {
        int index = Math.min(Math.max(padDirectWorkerRecoveryAttempt, 0), PAD_DIRECT_RECOVERY_BACKOFF_MS.length - 1);
        long delayMs = PAD_DIRECT_RECOVERY_BACKOFF_MS[index];
        padDirectWorkerRecoveryAttempt += 1;
        padDirectWorkerLastRecoveryDelayMs = delayMs;
        return delayMs;
    }

    private void resetPadDirectRecovery() {
        padDirectWorkerRecoveryAttempt = 0;
        padDirectWorkerLastRecoveryDelayMs = -1L;
    }

    private String formatPadDirectWorkerStatus() {
        long now = System.currentTimeMillis();
        String deviceId = preferences.getString(KEY_DEVICE_ID, "");
        String storeId = preferences.getString(KEY_DEVICE_STORE_ID, "");
        StringBuilder builder = new StringBuilder();
        builder.append("自动处理：")
            .append(isPadDirectAutoPrintEnabled() ? "开启" : "关闭，不会消费 PENDING 打印任务")
            .append('\n');
        builder.append("Worker 状态：").append(padDirectStateLabel(padDirectWorkerState)).append('\n');
        builder.append("运行中：").append(padDirectWorkerRunning && !padDirectWorkerStopRequested ? "是" : "否")
            .append(" | App 前台：").append(padDirectAppForeground ? "是" : "否")
            .append(" | Job处理中：").append(padDirectWorkerInProgress || padDirectJobInProgress ? "是" : "否")
            .append('\n');
        builder.append("Device ID: ").append(deviceId == null || deviceId.isBlank() ? "-" : deviceId)
            .append(" | Store ID: ").append(storeId == null || storeId.isBlank() ? "-" : storeId)
            .append('\n');
        builder.append("最近 poll：").append(timestampOrDash(padDirectWorkerLastPollAtMs))
            .append(" | 返回数量：").append(padDirectWorkerLastPollResultCount < 0 ? "-" : padDirectWorkerLastPollResultCount)
            .append(" | 耗时：").append(durationOrDash(padDirectWorkerLastPollDurationMs))
            .append('\n');
        builder.append("最近 poll 完成：").append(timestampOrDash(padDirectWorkerLastPollFinishedAtMs)).append('\n');
        builder.append("最老待处理年龄：").append(durationOrDash(padDirectWorkerLastOldestJobAgeMs))
            .append(" | 上次队列等待：").append(durationOrDash(padDirectWorkerLastJobQueueDelayMs))
            .append('\n');
        builder.append("上次任务耗时：").append(durationOrDash(padDirectWorkerLastJobProcessingDurationMs))
            .append(" | 连续错误：").append(padDirectWorkerConsecutiveErrors)
            .append('\n');
        if (padDirectWorkerState == PadDirectWorkerState.RECOVERING) {
            builder.append("恢复退避：").append(durationOrDash(padDirectWorkerLastRecoveryDelayMs))
                .append(" | 恢复次数：").append(padDirectWorkerRecoveryAttempt)
                .append('\n');
        }
        builder.append("下一次 poll：").append(padDirectWorkerPollScheduled ? "已安排 " + padDirectWorkerNextDelayMs + "ms" : "未安排")
            .append(" | Watchdog：").append(padDirectWorkerWatchdogScheduled ? "运行中" : "未安排")
            .append('\n');
        if (!padDirectWorkerCurrentJobId.isBlank()) {
            builder.append("当前任务：#").append(padDirectWorkerCurrentJobId)
                .append(" / ").append(padDirectWorkerCurrentModule.isBlank() ? "-" : padDirectWorkerCurrentModule)
                .append(" / ").append(padDirectWorkerCurrentPrinterEndpoint.isBlank() ? "-" : padDirectWorkerCurrentPrinterEndpoint)
                .append('\n');
        }
        if (!padDirectWorkerLastStartReason.isBlank()) {
            builder.append("最近启动：").append(padDirectWorkerLastStartReason).append('\n');
        }
        if (!padDirectWorkerLastStopReason.isBlank()) {
            builder.append("最近停止：").append(padDirectWorkerLastStopReason).append('\n');
        }
        if (!padDirectWorkerLastPollError.isBlank()) {
            builder.append("最近错误：").append(padDirectWorkerLastPollError).append('\n');
        }
        if (!isPadDirectAutoPrintEnabled()) {
            builder.append("提示：自动处理关闭，不会消费 PENDING 打印任务。").append('\n');
        }
        if (padDirectWorkerState == PadDirectWorkerState.ERROR_STOPPED) {
            builder.append("警告：自动打印已停止，请检查原因后重新开启。").append('\n');
        }
        if (padDirectWorkerState == PadDirectWorkerState.RECOVERING) {
            builder.append("提示：网络/API 异常，自动处理中仍开启，恢复后会继续检查打印任务。").append('\n');
        }
        if (padDirectWorkerLastOldestJobAgeMs > 120000L) {
            builder.append("警告：队列最前方有旧任务未处理，可能阻塞后续打印。请到 Print Center 检查。").append('\n');
        }
        if (isPadDirectWorkerPollStale(now)) {
            builder.append("警告：自动处理可能卡住，系统会尝试恢复；如仍不动，请关闭后重新开启。");
        }
        return builder.toString().trim();
    }

    private void updatePadDirectWorkerControls() {
        updatePadDirectKeepScreenOn();
        PadDirectWorkerControls controls = activePadDirectWorkerControls;
        if (controls == null) {
            return;
        }
        Runnable update = () -> {
            controls.autoStatus.setText(formatPadDirectWorkerStatus());
            boolean paired = isDevicePaired();
            boolean running = padDirectWorkerRunning && !padDirectWorkerStopRequested;
            controls.startAutoButton.setEnabled(paired && !running);
            controls.stopAutoButton.setEnabled(running);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update.run();
        } else {
            runOnUiThread(update);
        }
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
        long jobId = job.optLong("id", -1L);
        if (jobId <= 0) {
            resultView.setText("任务缺少有效 job id。");
            return;
        }
        String moduleCode = job.optString("module_code", "PAD_DIRECT");
        String attemptToken = "android-" + deviceId + "-" + jobId + "-" + System.currentTimeMillis();
        padDirectWorkerLastJobQueueDelayMs = ageMsFromTimestamp(job.optString("created_at", ""), System.currentTimeMillis());

        padDirectJobInProgress = true;
        printButton.setEnabled(false);
        refreshButton.setEnabled(false);
        resultView.setText("正在领取并打印 Job #" + jobId + " (" + moduleCode + ")...");

        new Thread(() -> {
            PadDirectJobResult result;
            try {
                result = executePadDirectJob(jobId, moduleCode, attemptToken);
            } catch (Exception exception) {
                result = PadDirectJobResult.stop(
                    "无法连接后端或处理任务失败: " + exception.getClass().getSimpleName() + " - " + exception.getMessage(),
                    false
                );
            }
            postUiAfterPadDirectJob(resultView, refreshButton, printButton, result.message, result.refreshAfter, jobsList, printerIpInput, printerPortInput, printerTimeoutInput);
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
            if (padDirectWorkerPendingKick && padDirectWorkerRunning && !padDirectWorkerStopRequested) {
                PadDirectWorkerControls controls = activePadDirectWorkerControls;
                if (controls != null) {
                    padDirectWorkerPendingKick = false;
                    padDirectQuickPollsRemaining = 2;
                    clearPadDirectWorkerCallbacks();
                    schedulePadDirectWorkerTick(controls, 0);
                }
            }
        });
    }

    private PadDirectJobResult executePadDirectJob(
        long jobId,
        String moduleCode,
        String attemptToken
    ) throws Exception {
        long jobStartedAt = System.currentTimeMillis();
        boolean claimed = false;
        boolean localPrintSent = false;
        try {
            padDirectWorkerCurrentJobId = String.valueOf(jobId);
            padDirectWorkerCurrentModule = moduleCode;
            setPadDirectWorkerState(PadDirectWorkerState.PROCESSING_JOB);
            Log.i(WORKER_TAG, "Worker Job Processing jobId=" + jobId + " step=claim module=" + moduleCode);
            long stepStartedAt = System.currentTimeMillis();
            JSONObject claimRequest = new JSONObject();
            claimRequest.put("client_attempt_token", attemptToken);
            claimRequest.put("lease_seconds", 300);
            HttpResult claimResult = postDeviceJson("/api/v1/printing/jobs/" + jobId + "/claim", claimRequest);
            logWorkerDuration("claim_duration_ms", jobId, stepStartedAt);
            if (claimResult.status == 401 || claimResult.status == 403) {
                Log.w(WORKER_TAG, "Worker Job Failed jobId=" + jobId + " errorCode=DEVICE_AUTH_FAILED httpStatus=" + claimResult.status);
                return PadDirectJobResult.stop("设备认证失败，请重新配对。", false);
            }
            if (claimResult.status == 409) {
                Log.i(WORKER_TAG, "Worker Job Failed jobId=" + jobId + " errorCode=CLAIM_CONFLICT module=" + moduleCode);
                return PadDirectJobResult.conflict("任务已被其他 Pad 领取。");
            }
            requireSuccessResponse(claimResult, "领取任务失败");
            claimed = true;

            Log.i(WORKER_TAG, "Worker Job Processing jobId=" + jobId + " step=start-print module=" + moduleCode);
            stepStartedAt = System.currentTimeMillis();
            JSONObject startPrintRequest = new JSONObject();
            startPrintRequest.put("client_attempt_token", attemptToken);
            startPrintRequest.put("lease_seconds", 300);
            HttpResult startPrintResult = postDeviceJson("/api/v1/printing/jobs/" + jobId + "/start-print", startPrintRequest);
            logWorkerDuration("start_print_duration_ms", jobId, stepStartedAt);
            requireSuccessResponse(startPrintResult, "标记开始打印失败");

            Log.i(WORKER_TAG, "Worker Job Processing jobId=" + jobId + " step=payload module=" + moduleCode);
            stepStartedAt = System.currentTimeMillis();
            HttpResult payloadResult = getDeviceJson("/api/v1/printing/jobs/" + jobId + "/payload");
            logWorkerDuration("payload_duration_ms", jobId, stepStartedAt);
            if (payloadResult.status == 409) {
                throw new PadDirectStepException(
                    "ANDROID_ASSIGNED_PRINTER_MISSING",
                    "打印任务缺少 assigned printer，或 assigned printer 不可用。请检查 Print Center 打印机分配。",
                    payloadResult.body
                );
            }
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
            AssignedPrinterEndpoint assignedPrinter = assignedPrinterFromPayload(payloadData);
            padDirectWorkerCurrentPrinterEndpoint = assignedPrinter.endpoint;
            updatePadDirectWorkerControls();

            Log.i(WORKER_TAG, "Worker Job Processing jobId=" + jobId + " step=native-print module=" + moduleCode + " printerEndpoint=" + assignedPrinter.endpoint);
            stepStartedAt = System.currentTimeMillis();
            NativePrintAttemptResult printResult = printAssignedPrinterWithConnectRetry(jobId, moduleCode, payloadBase64, assignedPrinter);
            logWorkerDuration("tcp_print_duration_ms", jobId, stepStartedAt);
            if (!printResult.success) {
                throw new PadDirectStepException(
                    mapNativePrintErrorCode(printResult),
                    buildNativePrintFailureMessage(jobId, moduleCode, assignedPrinter, printResult),
                    printResult.rawResult
                );
            }
            localPrintSent = true;

            Log.i(WORKER_TAG, "Worker Job Processing jobId=" + jobId + " step=complete module=" + moduleCode);
            stepStartedAt = System.currentTimeMillis();
            JSONObject completeRequest = new JSONObject();
            completeRequest.put("client_attempt_token", attemptToken);
            completeRequest.put("raw_result", printResult.rawResult);
            HttpResult completeResult = postDeviceJson("/api/v1/printing/jobs/" + jobId + "/complete", completeRequest);
            logWorkerDuration("complete_duration_ms", jobId, stepStartedAt);
            requireSuccessResponse(completeResult, "本地已打印，但回报后端完成失败。请检查 Print Center，避免重复打印");
            Log.i(WORKER_TAG, "Worker Job Finished jobId=" + jobId + " status=PRINTED module=" + moduleCode + " printerEndpoint=" + assignedPrinter.endpoint);
            return PadDirectJobResult.success("打印完成：Job #" + jobId + " (" + moduleCode + ") -> " + assignedPrinter.endpoint);
        } catch (PadDirectStepException exception) {
            String message = exception.getMessage();
            Log.e(WORKER_TAG, "Worker Job Failed jobId=" + jobId + " errorCode=" + exception.errorCode + " module=" + moduleCode + " message=" + message, exception);
            if (!localPrintSent && isRecoverableApiStep(exception)) {
                return PadDirectJobResult.recover(message + "\n网络/API 状态不确定，自动恢复中；不会自动重打，恢复后继续检查队列。");
            }
            if (claimed && !localPrintSent) {
                message = message + "\n" + reportPadDirectFail(jobId, attemptToken, exception.errorCode, exception.getMessage(), exception.rawResult);
                return PadDirectJobResult.stop(message, true);
            }
            if (localPrintSent) {
                return PadDirectJobResult.stop(message, true);
            }
            return PadDirectJobResult.stop(message, false);
        } catch (Exception exception) {
            String message = "无法连接后端或处理任务失败: " + exception.getClass().getSimpleName() + " - " + exception.getMessage();
            if (!localPrintSent) {
                return PadDirectJobResult.recover(message + "\n网络异常，自动恢复中；尚未完成本地出纸，不会关闭自动处理。");
            }
            if (claimed && !localPrintSent) {
                message = message + "\n" + reportPadDirectFail(jobId, attemptToken, "ANDROID_NETWORK_ERROR", message, exception.getMessage());
                return PadDirectJobResult.stop(message, true);
            }
            if (localPrintSent) {
                return PadDirectJobResult.stop(message, true);
            }
            return PadDirectJobResult.stop(message, false);
        }
        finally {
            padDirectWorkerLastJobProcessingDurationMs = Math.max(0L, System.currentTimeMillis() - jobStartedAt);
            Log.i(WORKER_TAG, "job_finished totalDurationMs=" + padDirectWorkerLastJobProcessingDurationMs + " jobId=" + jobId + " module=" + moduleCode);
        }
    }

    private NativePrintAttemptResult printAssignedPrinterWithConnectRetry(
        long jobId,
        String moduleCode,
        String payloadBase64,
        AssignedPrinterEndpoint assignedPrinter
    ) throws Exception {
        JSONArray attempts = new JSONArray();
        NativePrintAttemptResult lastResult = null;
        for (int attempt = 1; attempt <= PAD_DIRECT_NATIVE_PRINT_MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                long delay = PAD_DIRECT_CONNECT_RETRY_DELAYS_MS[Math.min(attempt - 1, PAD_DIRECT_CONNECT_RETRY_DELAYS_MS.length - 1)];
                Log.w(WORKER_TAG, "Worker Job Processing retry assigned printer connect jobId=" + jobId
                    + " module=" + moduleCode
                    + " printerEndpoint=" + assignedPrinter.endpoint
                    + " attempt=" + attempt + "/" + PAD_DIRECT_NATIVE_PRINT_MAX_ATTEMPTS
                    + " delay_ms=" + delay);
                Thread.sleep(delay);
            }

            JSONObject printRequest = new JSONObject();
            printRequest.put("ip", assignedPrinter.host);
            printRequest.put("port", assignedPrinter.port);
            printRequest.put("timeoutMs", assignedPrinter.timeoutMs);
            printRequest.put("payloadBase64", payloadBase64);
            Log.i(WORKER_TAG, "Worker Job Processing connecting assigned printer jobId=" + jobId
                + " module=" + moduleCode
                + " printerEndpoint=" + assignedPrinter.endpoint
                + " attempt=" + attempt + "/" + PAD_DIRECT_NATIVE_PRINT_MAX_ATTEMPTS);

            String rawPrintResult = printerPluginBridge.printRawTcp(printRequest.toString());
            NativePrintAttemptResult result = NativePrintAttemptResult.from(rawPrintResult, attempt);
            attempts.put(result.toSummaryJson());
            if (result.success) {
                result.rawResult = buildNativePrintRawResult(jobId, moduleCode, assignedPrinter, attempts, rawPrintResult);
                Log.i(WORKER_TAG, "Worker Job Finished native-send jobId=" + jobId
                    + " module=" + moduleCode
                    + " printerEndpoint=" + assignedPrinter.endpoint
                    + " attempt=" + attempt
                    + " bytes_written=" + result.bytesWritten);
                return result;
            }

            lastResult = result;
            if (isSafeConnectRetry(result) && attempt < PAD_DIRECT_NATIVE_PRINT_MAX_ATTEMPTS) {
                Log.w(WORKER_TAG, "Worker Job Failed assigned printer connect attempt will retry jobId=" + jobId
                    + " module=" + moduleCode
                    + " printerEndpoint=" + assignedPrinter.endpoint
                    + " native_error_code=" + result.nativeErrorCode
                    + " phase=" + result.phase
                    + " bytes_written=" + result.bytesWritten
                    + " attempt=" + attempt + "/" + PAD_DIRECT_NATIVE_PRINT_MAX_ATTEMPTS);
                continue;
            }

            if ("WRITE".equals(result.phase) || "FLUSH".equals(result.phase)) {
                Log.w(WORKER_TAG, "Worker Job Failed assigned printer failed after socket connect; not retrying because paper may be partial jobId="
                    + jobId
                    + " module=" + moduleCode
                    + " printerEndpoint=" + assignedPrinter.endpoint
                    + " native_error_code=" + result.nativeErrorCode
                    + " phase=" + result.phase
                    + " bytes_written=" + result.bytesWritten);
            } else {
                Log.w(WORKER_TAG, "Worker Job Failed assigned printer failed without safe retry jobId="
                    + jobId
                    + " module=" + moduleCode
                    + " printerEndpoint=" + assignedPrinter.endpoint
                    + " native_error_code=" + result.nativeErrorCode
                    + " phase=" + result.phase
                    + " bytes_written=" + result.bytesWritten);
            }
            break;
        }

        if (lastResult == null) {
            lastResult = NativePrintAttemptResult.failure("UNKNOWN", "UNKNOWN", 0, "", "", "", "No native print result", 0, "");
        }
        lastResult.rawResult = buildNativePrintRawResult(jobId, moduleCode, assignedPrinter, attempts, lastResult.rawResult);
        return lastResult;
    }

    private boolean isSafeConnectRetry(NativePrintAttemptResult result) {
        if (result == null) {
            return false;
        }
        if (!"CONNECT".equals(result.phase) || result.bytesWritten != 0) {
            return false;
        }
        return "TIMEOUT".equals(result.nativeErrorCode)
            || "CONNECTION_REFUSED".equals(result.nativeErrorCode)
            || "UNREACHABLE".equals(result.nativeErrorCode);
    }

    private String mapNativePrintErrorCode(NativePrintAttemptResult result) {
        String phase = result == null ? "UNKNOWN" : result.phase;
        String code = result == null ? "UNKNOWN" : result.nativeErrorCode;
        if ("CONNECT".equals(phase) && "TIMEOUT".equals(code)) {
            return "ANDROID_PRINTER_CONNECT_TIMEOUT";
        }
        if ("CONNECT".equals(phase) && "CONNECTION_REFUSED".equals(code)) {
            return "ANDROID_PRINTER_CONNECTION_REFUSED";
        }
        if ("CONNECT".equals(phase) && "UNREACHABLE".equals(code)) {
            return "ANDROID_PRINTER_NETWORK_UNREACHABLE";
        }
        if ("WRITE".equals(phase)) {
            return "ANDROID_PRINTER_WRITE_FAILED";
        }
        if ("FLUSH".equals(phase)) {
            return "ANDROID_PRINTER_FLUSH_FAILED";
        }
        return "ANDROID_NATIVE_PRINT_FAILED";
    }

    private String buildNativePrintFailureMessage(
        long jobId,
        String moduleCode,
        AssignedPrinterEndpoint assignedPrinter,
        NativePrintAttemptResult result
    ) {
        StringBuilder message = new StringBuilder();
        message.append("PAD_DIRECT 本机打印失败。")
            .append(" job_id=").append(jobId)
            .append("; module_code=").append(moduleCode)
            .append("; printer_id=").append(assignedPrinter.printerId == null ? "-" : assignedPrinter.printerId)
            .append("; printer_name=").append(assignedPrinter.printerName == null || assignedPrinter.printerName.isBlank() ? "-" : assignedPrinter.printerName)
            .append("; endpoint=").append(assignedPrinter.endpoint)
            .append("; device_id=").append(preferences.getString(KEY_DEVICE_ID, "-"))
            .append("; native_error_code=").append(result.nativeErrorCode)
            .append("; phase=").append(result.phase)
            .append("; bytes_written=").append(result.bytesWritten)
            .append("; attempts=").append(result.attemptNumber).append("/").append(PAD_DIRECT_NATIVE_PRINT_MAX_ATTEMPTS);
        if (result.exceptionClass != null && !result.exceptionClass.isBlank()) {
            message.append("; exception_class=").append(result.exceptionClass);
        }
        if (result.exceptionMessage != null && !result.exceptionMessage.isBlank()) {
            message.append("; exception_message=").append(truncateForJson(result.exceptionMessage, 240));
        }
        if ("WRITE".equals(result.phase) || "FLUSH".equals(result.phase)) {
            message.append("\n写入/flush 过程中失败，可能已经部分出纸，请人工确认后再重打。");
        } else if (isSafeConnectRetry(result)) {
            message.append("\n连接阶段失败且未写入任何字节，已完成安全短重试后停止。请检查打印机电源、WiFi、IP、端口和 VLAN。");
        } else {
            message.append("\n本机打印失败，请检查 assigned printer 后人工重打。");
        }
        return message.toString();
    }

    private String buildNativePrintRawResult(
        long jobId,
        String moduleCode,
        AssignedPrinterEndpoint assignedPrinter,
        JSONArray attempts,
        String finalRawResult
    ) {
        try {
            JSONObject raw = new JSONObject();
            raw.put("job_id", jobId);
            raw.put("module_code", moduleCode);
            raw.put("printer_id", assignedPrinter.printerId);
            raw.put("printer_name", assignedPrinter.printerName);
            raw.put("printer_endpoint", assignedPrinter.endpoint);
            raw.put("attempts", attempts);
            if (finalRawResult != null && !finalRawResult.isBlank()) {
                try {
                    raw.put("final_result", new JSONObject(finalRawResult));
                } catch (Exception ignored) {
                    raw.put("final_result_raw", truncateForJson(finalRawResult, 1000));
                }
            }
            return raw.toString();
        } catch (Exception exception) {
            return finalRawResult == null ? "" : finalRawResult;
        }
    }

    private AssignedPrinterEndpoint assignedPrinterFromPayload(JSONObject payloadData) throws PadDirectStepException {
        String host = payloadData.optString("printer_host", "").trim();
        int port = payloadData.optInt("printer_port", -1);
        if (host.isBlank() || port <= 0 || port > 65535) {
            throw new PadDirectStepException(
                "ANDROID_ASSIGNED_PRINTER_MISSING",
                "打印任务缺少 assigned printer。请检查 Print Center 中该模块的打印机分配。",
                payloadData.toString()
            );
        }
        int timeoutMs = payloadData.optInt("timeout_ms", DEFAULT_PRINTER_TEST_TIMEOUT_MS);
        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_PRINTER_TEST_TIMEOUT_MS;
        }
        timeoutMs = parseTimeoutMs(String.valueOf(timeoutMs));
        String endpoint = payloadData.optString("printer_endpoint", "").trim();
        if (endpoint.isBlank() || "null".equalsIgnoreCase(endpoint)) {
            endpoint = host + ":" + port;
        }
        Long printerId = payloadData.has("printer_id") && !payloadData.isNull("printer_id") ? payloadData.optLong("printer_id") : null;
        String printerName = payloadData.optString("printer_name", "").trim();
        return new AssignedPrinterEndpoint(host, port, timeoutMs, endpoint, printerId, printerName);
    }

    private void autoStartPadDirectWorkerIfNeeded(
        TextView autoStatus,
        TextView resultView,
        LinearLayout jobsList,
        Button refreshButton,
        Button startAutoButton,
        Button stopAutoButton,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput,
        TextView warningView
    ) {
        if (padDirectWorkerRunning && !padDirectWorkerStopRequested) {
            warningView.setText("");
            rememberPadDirectWorkerControls(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput);
            updatePadDirectWorkerControls();
            Log.i(WORKER_TAG, "Worker Started skipped: Control Panel attached to existing PAD_DIRECT worker");
            return;
        }
        if (!isDevicePaired()) {
            String message = "Warning: 自动打印处理服务未启动，请先配对本机 Pad。";
            Log.w(WORKER_TAG, message);
            warningView.setText(message);
            autoStatus.setText(formatPadDirectWorkerStatus() + "\n请先配对本机 Pad。");
            startAutoButton.setEnabled(false);
            stopAutoButton.setEnabled(false);
            return;
        }
        if (!isPadDirectAutoPrintEnabled()) {
            warningView.setText("");
            rememberPadDirectWorkerControls(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput);
            autoStatus.setText(formatPadDirectWorkerStatus());
            startAutoButton.setEnabled(true);
            stopAutoButton.setEnabled(false);
            Log.i(WORKER_TAG, "Worker Started skipped: auto processing disabled by user");
            return;
        }
        if (padDirectWorkerErrorStopped) {
            String message = "Warning: 自动打印上次异常停止，请检查原因后手动重新开启。";
            warningView.setText(message);
            rememberPadDirectWorkerControls(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput);
            autoStatus.setText(formatPadDirectWorkerStatus());
            startAutoButton.setEnabled(true);
            stopAutoButton.setEnabled(false);
            Log.i(WORKER_TAG, "Worker Started skipped: previous error stop requires manual restart");
            return;
        }
        try {
            warningView.setText("");
            startPadDirectAutoWorker(
                autoStatus,
                resultView,
                jobsList,
                refreshButton,
                startAutoButton,
                stopAutoButton,
                printerIpInput,
                printerPortInput,
                printerTimeoutInput,
                "control-panel-auto-start"
            );
        } catch (Exception exception) {
            String message = "Warning: 自动打印处理服务启动失败：" + exception.getMessage();
            Log.e(WORKER_TAG, "Worker Exception: failed to auto-start Pad Direct print processing worker", exception);
            warningView.setText(message);
            padDirectWorkerLastPollError = message;
            setPadDirectWorkerState(PadDirectWorkerState.ERROR_STOPPED);
            autoStatus.setText(formatPadDirectWorkerStatus());
        }
    }

    private void startPadDirectAutoWorker(
        TextView autoStatus,
        TextView resultView,
        LinearLayout jobsList,
        Button refreshButton,
        Button startAutoButton,
        Button stopAutoButton,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput,
        String startReason
    ) {
        if (padDirectWorkerRunning) {
            Log.i(WORKER_TAG, "Worker Started skipped: PAD_DIRECT print processing worker already running");
            rememberPadDirectWorkerControls(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput);
            updatePadDirectWorkerControls();
            return;
        }
        if (!isDevicePaired()) {
            Log.w(WORKER_TAG, "Worker Started failed: Pad is not paired");
            padDirectWorkerLastStopReason = "not-paired";
            setPadDirectWorkerState(PadDirectWorkerState.ERROR_STOPPED);
            autoStatus.setText(formatPadDirectWorkerStatus() + "\n设备认证失败，已停止。请先配对本机 Pad。");
            updatePadDirectWorkerControls();
            return;
        }
        if (!padDirectAppForeground) {
            Log.i(WORKER_TAG, "Worker Started skipped: app is not foreground");
            padDirectWorkerLastStopReason = "app-not-foreground";
            autoStatus.setText(formatPadDirectWorkerStatus() + "\nApp 不在前台，暂不启动自动处理。");
            updatePadDirectWorkerControls();
            return;
        }
        setPadDirectAutoPrintEnabled(true);
        padDirectWorkerRunning = true;
        padDirectWorkerStopRequested = false;
        padDirectWorkerPendingKick = false;
        padDirectQuickPollsRemaining = 0;
        padDirectWorkerStoppedForLifecycle = false;
        padDirectWorkerUserStopped = false;
        padDirectWorkerErrorStopped = false;
        padDirectWorkerLastPollError = "";
        padDirectWorkerLastStartReason = startReason == null || startReason.isBlank() ? "manual/control-panel" : startReason;
        padDirectWorkerLastStopReason = "";
        padDirectWorkerConsecutiveErrors = 0;
        resetPadDirectRecovery();
        clearPadDirectCurrentJob();
        setPadDirectWorkerState(PadDirectWorkerState.STARTING);
        rememberPadDirectWorkerControls(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput);
        startAutoButton.setEnabled(false);
        stopAutoButton.setEnabled(true);
        autoStatus.setText(formatPadDirectWorkerStatus());
        Log.i(WORKER_TAG, "Worker Started reason=" + padDirectWorkerLastStartReason);
        Log.i(WORKER_TAG, "worker_started reason=" + padDirectWorkerLastStartReason);
        setPadDirectWorkerState(PadDirectWorkerState.WAITING);
        schedulePadDirectWorkerTick(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput, 0);
        schedulePadDirectWorkerWatchdog();
    }

    private void stopPadDirectAutoWorker(TextView autoStatus, Button startAutoButton, Button stopAutoButton, String message) {
        stopPadDirectAutoWorker(autoStatus, startAutoButton, stopAutoButton, message, message, false);
    }

    private void stopPadDirectAutoWorker(
        TextView autoStatus,
        Button startAutoButton,
        Button stopAutoButton,
        String message,
        String reason,
        boolean errorStopped
    ) {
        setPadDirectWorkerState(errorStopped ? PadDirectWorkerState.ERROR_STOPPED : PadDirectWorkerState.STOPPING);
        padDirectWorkerStopRequested = true;
        padDirectWorkerRunning = false;
        padDirectWorkerPendingKick = false;
        padDirectQuickPollsRemaining = 0;
        padDirectWorkerStoppedForLifecycle = false;
        padDirectWorkerErrorStopped = errorStopped;
        padDirectWorkerLastStopReason = reason == null || reason.isBlank() ? message : reason;
        clearPadDirectCurrentJob();
        activePadDirectWorkerControls = null;
        clearPadDirectWorkerCallbacks();
        updatePadDirectKeepScreenOn();
        setPadDirectWorkerState(errorStopped ? PadDirectWorkerState.ERROR_STOPPED : PadDirectWorkerState.STOPPED);
        startAutoButton.setEnabled(isDevicePaired());
        stopAutoButton.setEnabled(false);
        autoStatus.setText(formatPadDirectWorkerStatus() + "\n" + message);
        Log.i(WORKER_TAG, "Worker Stopped reason=" + padDirectWorkerLastStopReason);
        Log.i(WORKER_TAG, "worker_stopped reason=" + padDirectWorkerLastStopReason);
    }

    private void rememberPadDirectWorkerControls(
        TextView autoStatus,
        TextView resultView,
        LinearLayout jobsList,
        Button refreshButton,
        Button startAutoButton,
        Button stopAutoButton,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput
    ) {
        activePadDirectWorkerControls = new PadDirectWorkerControls(
            autoStatus,
            resultView,
            jobsList,
            refreshButton,
            startAutoButton,
            stopAutoButton,
            printerIpInput,
            printerPortInput,
            printerTimeoutInput
        );
        updatePadDirectWorkerControls();
    }

    private void startPadDirectWorkerHeadlessIfPaired(String reason) {
        if (padDirectWorkerRunning && !padDirectWorkerStopRequested) {
            return;
        }
        if (!padDirectAppForeground) {
            Log.i(WORKER_TAG, "Worker Started skipped: app not foreground reason=" + reason);
            return;
        }
        if (!isPadDirectAutoPrintEnabled()) {
            Log.i(WORKER_TAG, "Worker Started skipped: auto processing disabled reason=" + reason);
            return;
        }
        if (padDirectWorkerErrorStopped) {
            Log.i(WORKER_TAG, "Worker Started skipped: previous worker error requires manual restart reason=" + reason);
            return;
        }
        if (padDirectWorkerUserStopped) {
            Log.i(WORKER_TAG, "Worker Started skipped: user manually stopped PAD_DIRECT worker");
            return;
        }
        if (!isDevicePaired()) {
            Log.i(WORKER_TAG, "Worker Started skipped: Pad is not paired");
            return;
        }
        TextView autoStatus = new TextView(this);
        TextView resultView = new TextView(this);
        LinearLayout jobsList = new LinearLayout(this);
        jobsList.setOrientation(LinearLayout.VERTICAL);
        Button refreshButton = new Button(this);
        Button startAutoButton = new Button(this);
        Button stopAutoButton = new Button(this);
        EditText printerIpInput = new EditText(this);
        printerIpInput.setText(getPrinterTestIp());
        EditText printerPortInput = new EditText(this);
        printerPortInput.setText(String.valueOf(getPrinterTestPort()));
        EditText printerTimeoutInput = new EditText(this);
        printerTimeoutInput.setText(String.valueOf(getPrinterTestTimeoutMs()));
        Log.i(WORKER_TAG, "Worker Started: auto-starting headless PAD_DIRECT worker reason=" + reason);
        startPadDirectAutoWorker(
            autoStatus,
            resultView,
            jobsList,
            refreshButton,
            startAutoButton,
            stopAutoButton,
            printerIpInput,
            printerPortInput,
            printerTimeoutInput,
            reason
        );
        updatePadDirectWorkerControls();
    }

    private void schedulePadDirectWorkerTick(
        TextView autoStatus,
        TextView resultView,
        LinearLayout jobsList,
        Button refreshButton,
        Button startAutoButton,
        Button stopAutoButton,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput,
        long delayMs
    ) {
        rememberPadDirectWorkerControls(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput);
        padDirectWorkerPollScheduled = true;
        padDirectWorkerLastScheduledAtMs = System.currentTimeMillis();
        padDirectWorkerNextDelayMs = delayMs;
        Log.i(WORKER_TAG, "Worker Poll Scheduled delayMs=" + delayMs);
        updatePadDirectWorkerControls();
        padDirectWorkerHandler.postDelayed(() -> runPadDirectWorkerTick(
            autoStatus,
            resultView,
            jobsList,
            refreshButton,
            startAutoButton,
            stopAutoButton,
            printerIpInput,
            printerPortInput,
            printerTimeoutInput
        ), delayMs);
        schedulePadDirectWorkerWatchdog();
    }

    private void schedulePadDirectWorkerTick(PadDirectWorkerControls controls, long delayMs) {
        if (controls == null) {
            return;
        }
        schedulePadDirectWorkerTick(
            controls.autoStatus,
            controls.resultView,
            controls.jobsList,
            controls.refreshButton,
            controls.startAutoButton,
            controls.stopAutoButton,
            controls.printerIpInput,
            controls.printerPortInput,
            controls.printerTimeoutInput,
            delayMs
        );
    }

    private void schedulePadDirectWorkerWatchdog() {
        if (!padDirectWorkerRunning || padDirectWorkerStopRequested) {
            return;
        }
        padDirectWorkerHandler.removeCallbacks(padDirectWorkerWatchdog);
        padDirectWorkerWatchdogScheduled = true;
        padDirectWorkerHandler.postDelayed(padDirectWorkerWatchdog, PAD_DIRECT_WATCHDOG_INTERVAL_MS);
    }

    private void runPadDirectWorkerWatchdog() {
        padDirectWorkerWatchdogScheduled = false;
        if (!padDirectWorkerRunning || padDirectWorkerStopRequested) {
            return;
        }
        if (!padDirectAppForeground || !isPadDirectAutoPrintEnabled()) {
            PadDirectWorkerControls controls = activePadDirectWorkerControls;
            if (controls != null) {
                stopPadDirectAutoWorker(
                    controls.autoStatus,
                    controls.startAutoButton,
                    controls.stopAutoButton,
                    "已停止。App 不在前台或自动处理已关闭。",
                    padDirectAppForeground ? "auto-disabled" : "app-background",
                    false
                );
            } else {
                padDirectWorkerRunning = false;
                padDirectWorkerStopRequested = true;
                padDirectWorkerLastStopReason = padDirectAppForeground ? "auto-disabled" : "app-background";
                setPadDirectWorkerState(PadDirectWorkerState.STOPPED);
                clearPadDirectWorkerCallbacks();
            }
            return;
        }
        if (padDirectWorkerInProgress || padDirectJobInProgress) {
            schedulePadDirectWorkerWatchdog();
            return;
        }
        long now = System.currentTimeMillis();
        if (isPadDirectWorkerPollStale(now)) {
            Log.w(WORKER_TAG, "Worker Watchdog Rescheduled lastPollAt=" + timestampOrDash(padDirectWorkerLastPollAtMs)
                + " pollScheduled=" + padDirectWorkerPollScheduled);
            padDirectWorkerLastPollError = "Watchdog rescheduled worker poll after stale tick.";
            PadDirectWorkerControls controls = activePadDirectWorkerControls;
            if (controls == null) {
                padDirectWorkerLastStopReason = "watchdog-no-controls";
                padDirectWorkerRunning = false;
                padDirectWorkerStopRequested = true;
                setPadDirectWorkerState(PadDirectWorkerState.ERROR_STOPPED);
                clearPadDirectWorkerCallbacks();
                return;
            }
            clearPadDirectWorkerCallbacks();
            schedulePadDirectWorkerTick(controls, 0);
            return;
        }
        schedulePadDirectWorkerWatchdog();
        updatePadDirectWorkerControls();
    }

    private void resumePadDirectWorkerAfterLifecycleIfNeeded() {
        if (!padDirectWorkerStoppedForLifecycle) {
            return;
        }
        PadDirectWorkerControls controls = activePadDirectWorkerControls;
        if (controls == null) {
            Log.w(WORKER_TAG, "Worker Started failed after lifecycle resume: no active worker controls");
            padDirectWorkerStoppedForLifecycle = false;
            return;
        }
        if (!isDevicePaired()) {
            Log.w(WORKER_TAG, "Worker Started failed after lifecycle resume: Pad is not paired");
            padDirectWorkerStoppedForLifecycle = false;
            controls.autoStatus.setText("自动处理打印任务：未恢复，请先重新配对本机 Pad。");
            controls.startAutoButton.setEnabled(false);
            controls.stopAutoButton.setEnabled(false);
            return;
        }
        if (!padDirectAppForeground || !isPadDirectAutoPrintEnabled()) {
            Log.i(WORKER_TAG, "Worker Started skipped after lifecycle resume: appForeground="
                + padDirectAppForeground + " autoEnabled=" + isPadDirectAutoPrintEnabled());
            padDirectWorkerStoppedForLifecycle = false;
            updatePadDirectWorkerControls();
            return;
        }
        if (padDirectWorkerRunning && !padDirectWorkerStopRequested) {
            padDirectWorkerStoppedForLifecycle = false;
            return;
        }
        padDirectWorkerRunning = true;
        padDirectWorkerStopRequested = false;
        padDirectWorkerInProgress = false;
        padDirectWorkerPendingKick = false;
        padDirectQuickPollsRemaining = 0;
        padDirectWorkerStoppedForLifecycle = false;
        padDirectWorkerErrorStopped = false;
        padDirectWorkerLastStartReason = "auto-resume";
        padDirectWorkerLastStopReason = "";
        padDirectWorkerLastPollError = "";
        resetPadDirectRecovery();
        setPadDirectWorkerState(PadDirectWorkerState.WAITING);
        controls.startAutoButton.setEnabled(false);
        controls.stopAutoButton.setEnabled(true);
        controls.autoStatus.setText(formatPadDirectWorkerStatus() + "\nApp 回到前台，已恢复处理服务。");
        Log.i(WORKER_TAG, "Worker Started reason=auto-resume");
        Log.i(WORKER_TAG, "worker_started reason=auto-resume");
        schedulePadDirectWorkerTick(controls, 0);
        schedulePadDirectWorkerWatchdog();
    }

    private void schedulePadDirectQuickKickWindow(String reason, long firstDelayMs) {
        if (!padDirectWorkerRunning || padDirectWorkerStopRequested) {
            return;
        }
        if (padDirectWorkerInProgress || padDirectJobInProgress) {
            padDirectWorkerPendingKick = true;
            Log.i(WORKER_TAG, "Worker Poll Scheduled pendingKick=true reason=" + reason + " workerBusy=true");
            return;
        }
        PadDirectWorkerControls controls = activePadDirectWorkerControls;
        if (controls == null) {
            padDirectWorkerPendingKick = true;
            Log.i(WORKER_TAG, "Worker Poll Scheduled pendingKick=true reason=" + reason + " noControls=true");
            return;
        }
        padDirectWorkerPendingKick = false;
        padDirectQuickPollsRemaining = 2;
        clearPadDirectWorkerCallbacks();
        String suffix = reason == null || reason.isBlank() ? "" : "\n触发来源：" + reason;
        controls.autoStatus.setText(formatPadDirectWorkerStatus() + "\n收到当前 Pad 提交订单触发，准备快速检查打印任务。" + suffix);
        Log.i(WORKER_TAG, "Worker Poll Scheduled quickKick reason=" + reason + " firstDelayMs=" + firstDelayMs);
        schedulePadDirectWorkerTick(controls, firstDelayMs);
    }

    private JSONObject buildPadDirectWorkerStatusJson() throws Exception {
        JSONObject response = new JSONObject();
        response.put("auto_enabled", isPadDirectAutoPrintEnabled());
        response.put("worker_running", padDirectWorkerRunning && !padDirectWorkerStopRequested);
        response.put("worker_state", padDirectWorkerState.name());
        response.put("worker_state_label", padDirectStateLabel(padDirectWorkerState));
        response.put("app_foreground", padDirectAppForeground);
        response.put("job_in_progress", padDirectWorkerInProgress || padDirectJobInProgress);
        response.put("recovering", padDirectWorkerState == PadDirectWorkerState.RECOVERING);
        response.put("error_stopped", padDirectWorkerErrorStopped || padDirectWorkerState == PadDirectWorkerState.ERROR_STOPPED);
        response.put("user_stopped", padDirectWorkerUserStopped);
        response.put("poll_scheduled", padDirectWorkerPollScheduled);
        response.put("watchdog_scheduled", padDirectWorkerWatchdogScheduled);
        response.put("last_poll_at_ms", padDirectWorkerLastPollAtMs > 0 ? padDirectWorkerLastPollAtMs : JSONObject.NULL);
        response.put("last_poll_finished_at_ms", padDirectWorkerLastPollFinishedAtMs > 0 ? padDirectWorkerLastPollFinishedAtMs : JSONObject.NULL);
        response.put("last_poll_result_count", padDirectWorkerLastPollResultCount);
        response.put("last_poll_duration_ms", padDirectWorkerLastPollDurationMs);
        response.put("last_error", padDirectWorkerLastPollError == null || padDirectWorkerLastPollError.isBlank() ? JSONObject.NULL : padDirectWorkerLastPollError);
        response.put("last_stop_reason", padDirectWorkerLastStopReason == null || padDirectWorkerLastStopReason.isBlank() ? JSONObject.NULL : padDirectWorkerLastStopReason);
        response.put("last_start_reason", padDirectWorkerLastStartReason == null || padDirectWorkerLastStartReason.isBlank() ? JSONObject.NULL : padDirectWorkerLastStartReason);
        response.put("next_delay_ms", padDirectWorkerNextDelayMs);
        response.put("recovery_delay_ms", padDirectWorkerLastRecoveryDelayMs);
        response.put("recovery_attempt", padDirectWorkerRecoveryAttempt);
        response.put("consecutive_errors", padDirectWorkerConsecutiveErrors);
        response.put("current_job_id", padDirectWorkerCurrentJobId == null || padDirectWorkerCurrentJobId.isBlank() ? JSONObject.NULL : padDirectWorkerCurrentJobId);
        response.put("current_module", padDirectWorkerCurrentModule == null || padDirectWorkerCurrentModule.isBlank() ? JSONObject.NULL : padDirectWorkerCurrentModule);
        response.put("current_printer_endpoint", padDirectWorkerCurrentPrinterEndpoint == null || padDirectWorkerCurrentPrinterEndpoint.isBlank() ? JSONObject.NULL : padDirectWorkerCurrentPrinterEndpoint);
        response.put("device_id", preferences.getString(KEY_DEVICE_ID, ""));
        response.put("store_id", preferences.getString(KEY_DEVICE_STORE_ID, ""));
        response.put("message", formatPadDirectWorkerStatus());
        return response;
    }

    private String kickPadDirectWorkerFromWeb(String json) {
        String parsedReason = "web-order-submit";
        boolean recoverErrorStopped = false;
        try {
            JSONObject request = new JSONObject(json == null || json.isBlank() ? "{}" : json);
            parsedReason = request.optString("reason", parsedReason);
            recoverErrorStopped = request.optBoolean("recover_error_stopped", false);
        } catch (Exception ignored) {
            // Ignore malformed optional metadata; the kick itself remains safe.
        }
        String reason = parsedReason;
        try {
            JSONObject response = new JSONObject();
            if (!isDevicePaired()) {
                response.put("accepted", false);
                response.put("status", "not_paired");
                response.put("message", "Pad is not paired; kick ignored.");
                return jsonSuccess(response);
            }
            if (!isPadDirectAutoPrintEnabled()) {
                response.put("accepted", false);
                response.put("status", "auto_disabled");
                response.put("message", "Auto print is disabled by user; kick ignored.");
                return jsonSuccess(response);
            }
            if (padDirectWorkerErrorStopped || padDirectWorkerState == PadDirectWorkerState.ERROR_STOPPED) {
                if (!recoverErrorStopped) {
                    response.put("accepted", false);
                    response.put("status", "error_stopped");
                    response.put("message", "Worker is error-stopped; operator confirmation is required.");
                    return jsonSuccess(response);
                }
                padDirectWorkerErrorStopped = false;
                padDirectWorkerStopRequested = false;
                padDirectWorkerLastStopReason = "";
                padDirectWorkerLastPollError = "";
                resetPadDirectRecovery();
                setPadDirectWorkerState(PadDirectWorkerState.WAITING);
                Log.i(WORKER_TAG, "Worker Started reason=" + reason + "-recover-error-stopped");
                startPadDirectWorkerHeadlessIfPaired(reason + "-recover-error-stopped");
            }
            if (!padDirectWorkerRunning || padDirectWorkerStopRequested) {
                startPadDirectWorkerHeadlessIfPaired(reason + "-wake");
            }
            response.put("worker_running", padDirectWorkerRunning && !padDirectWorkerStopRequested);
            if (!padDirectWorkerRunning || padDirectWorkerStopRequested) {
                response.put("accepted", false);
                response.put("status", "ignored_worker_off");
                response.put("message", "Semi-auto worker is not running; kick ignored.");
                return jsonSuccess(response);
            }
            if (padDirectWorkerInProgress || padDirectJobInProgress) {
                padDirectWorkerPendingKick = true;
                response.put("accepted", true);
                response.put("status", "queued_busy");
                response.put("message", "Worker is busy; quick poll will run after current job finishes.");
                return jsonSuccess(response);
            }
            long firstDelayMs = padDirectWorkerState == PadDirectWorkerState.RECOVERING ? 0 : PAD_DIRECT_KICK_FIRST_DELAY_MS;
            runOnUiThread(() -> schedulePadDirectQuickKickWindow(reason, firstDelayMs));
            response.put("accepted", true);
            response.put("status", padDirectWorkerState == PadDirectWorkerState.RECOVERING ? "scheduled_recovery_poll" : "scheduled_quick_window");
            response.put("first_delay_ms", firstDelayMs);
            response.put("retry_delay_ms", PAD_DIRECT_KICK_RETRY_DELAY_MS);
            return jsonSuccess(response);
        } catch (Exception exception) {
            return jsonFailure(exception.getMessage() == null ? "Failed to kick print worker" : exception.getMessage());
        }
    }

    private void runPadDirectWorkerTick(
        TextView autoStatus,
        TextView resultView,
        LinearLayout jobsList,
        Button refreshButton,
        Button startAutoButton,
        Button stopAutoButton,
        EditText printerIpInput,
        EditText printerPortInput,
        EditText printerTimeoutInput
    ) {
        padDirectWorkerPollScheduled = false;
        padDirectWorkerNextDelayMs = -1L;
        if (!padDirectWorkerRunning || padDirectWorkerStopRequested) {
            Log.i(WORKER_TAG, "Worker Stopped reason=tick-ignored-not-running");
            stopPadDirectAutoWorker(autoStatus, startAutoButton, stopAutoButton, "已停止。", "tick-ignored-not-running", false);
            return;
        }
        if (!padDirectAppForeground || !isPadDirectAutoPrintEnabled()) {
            String reason = padDirectAppForeground ? "auto-disabled" : "app-background";
            Log.i(WORKER_TAG, "Worker Stopped reason=" + reason);
            stopPadDirectAutoWorker(autoStatus, startAutoButton, stopAutoButton, "已停止。App 不在前台或自动处理已关闭。", reason, false);
            return;
        }
        if (padDirectWorkerInProgress || padDirectJobInProgress) {
            Log.d(WORKER_TAG, "Worker Poll Queue delayed: another PAD_DIRECT job is already in progress");
            schedulePadDirectWorkerTick(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput, 1000);
            return;
        }
        if (!isDevicePaired()) {
            Log.w(WORKER_TAG, "Worker Exception: device pairing missing during worker tick");
            padDirectWorkerLastPollError = "device pairing missing during worker tick";
            stopPadDirectAutoWorker(autoStatus, startAutoButton, stopAutoButton, "设备认证失败，已停止。请重新配对。", "auth-failed", true);
            return;
        }
        padDirectWorkerInProgress = true;
        setPadDirectWorkerState(PadDirectWorkerState.POLLING);
        padDirectWorkerLastPollAtMs = System.currentTimeMillis();
        updatePadDirectWorkerControls();
        new Thread(() -> {
            PadDirectJobResult workerResult;
            JSONObject job = null;
            try {
                String storeId = preferences.getString(KEY_DEVICE_STORE_ID, "");
                String deviceId = preferences.getString(KEY_DEVICE_ID, "");
                Log.i(WORKER_TAG, "Worker Poll Started deviceId=" + deviceId + " storeId=" + storeId);
                Log.i(WORKER_TAG, "poll_start deviceId=" + deviceId + " storeId=" + storeId);
                HttpResult pendingResult = getDeviceJson("/api/v1/stores/" + storeId + "/printing/jobs/pending?limit=10");
                if (pendingResult.status == 401 || pendingResult.status == 403) {
                    Log.w(WORKER_TAG, "Worker Exception: pending queue auth failed HTTP " + pendingResult.status);
                    padDirectWorkerLastPollError = "pending queue auth failed HTTP " + pendingResult.status;
                    padDirectWorkerConsecutiveErrors += 1;
                    workerResult = PadDirectJobResult.stop("设备认证失败，请重新配对。", false);
                } else {
                    requireSuccessResponse(pendingResult, "加载待打印任务失败");
                    JSONArray jobs = new JSONObject(pendingResult.body).optJSONArray("data");
                    if (jobs == null || jobs.length() == 0) {
                        padDirectWorkerLastPollResultCount = 0;
                        padDirectWorkerLastOldestJobAgeMs = -1L;
                        padDirectWorkerLastPollError = "";
                        padDirectWorkerConsecutiveErrors = 0;
                        Log.d(WORKER_TAG, "Worker Poll Result count=0");
                        workerResult = PadDirectJobResult.idle("正在等待任务。");
                    } else {
                        padDirectWorkerLastPollResultCount = jobs.length();
                        padDirectWorkerLastPollError = "";
                        padDirectWorkerConsecutiveErrors = 0;
                        job = jobs.getJSONObject(0);
                        long jobId = job.optLong("id", -1L);
                        String moduleCode = job.optString("module_code", "PAD_DIRECT");
                        String attemptToken = "android-auto-" + deviceId + "-" + jobId + "-" + System.currentTimeMillis();
                        long nowMs = System.currentTimeMillis();
                        padDirectWorkerLastOldestJobAgeMs = ageMsFromTimestamp(job.optString("created_at", ""), nowMs);
                        padDirectWorkerLastJobQueueDelayMs = padDirectWorkerLastOldestJobAgeMs;
                        padDirectWorkerCurrentJobId = String.valueOf(jobId);
                        padDirectWorkerCurrentModule = moduleCode;
                        padDirectWorkerCurrentPrinterEndpoint = job.optString("printer_endpoint", "");
                        setPadDirectWorkerState(PadDirectWorkerState.PROCESSING_JOB);
                        Log.i(WORKER_TAG, "Worker Poll Result count=" + jobs.length() + " oldestJobAgeMs=" + padDirectWorkerLastOldestJobAgeMs);
                        Log.i(WORKER_TAG, "Worker Picked jobId=" + jobId
                            + " module=" + moduleCode
                            + " queueDelayMs=" + padDirectWorkerLastJobQueueDelayMs
                            + " printerEndpoint=" + padDirectWorkerCurrentPrinterEndpoint);
                        Log.i(WORKER_TAG, "job_picked jobId=" + jobId
                            + " module=" + moduleCode
                            + " queueDelayMs=" + padDirectWorkerLastJobQueueDelayMs
                            + " printerEndpoint=" + padDirectWorkerCurrentPrinterEndpoint);
                        workerResult = executePadDirectJob(jobId, moduleCode, attemptToken);
                    }
                }
            } catch (PadDirectStepException exception) {
                padDirectWorkerLastPollError = exception.errorCode + " - " + exception.getMessage();
                padDirectWorkerConsecutiveErrors += 1;
                Log.e(WORKER_TAG, "Worker Exception exceptionClass=" + exception.getClass().getSimpleName() + " message=" + exception.getMessage(), exception);
                if (isRecoverableApiStep(exception)) {
                    workerResult = PadDirectJobResult.recover("加载待打印任务失败，网络异常，自动恢复中。");
                } else {
                    workerResult = PadDirectJobResult.stop(exception.getMessage(), false);
                }
            } catch (Exception exception) {
                padDirectWorkerLastPollError = exception.getClass().getSimpleName() + " - " + exception.getMessage();
                padDirectWorkerConsecutiveErrors += 1;
                Log.e(WORKER_TAG, "Worker Exception exceptionClass=" + exception.getClass().getSimpleName() + " message=" + exception.getMessage(), exception);
                workerResult = PadDirectJobResult.recover(recoverableWorkerMessage("后端连接失败", exception));
            }
            PadDirectJobResult finalResult = workerResult;
            JSONObject finalJob = job;
            runOnUiThread(() -> {
                padDirectWorkerInProgress = false;
                padDirectWorkerLastPollFinishedAtMs = System.currentTimeMillis();
                padDirectWorkerLastPollDurationMs = Math.max(0L, padDirectWorkerLastPollFinishedAtMs - padDirectWorkerLastPollAtMs);
                Log.i(WORKER_TAG, "Worker Poll End durationMs=" + padDirectWorkerLastPollDurationMs
                    + " resultCount=" + padDirectWorkerLastPollResultCount
                    + " oldestJobAgeMs=" + padDirectWorkerLastOldestJobAgeMs);
                Log.i(WORKER_TAG, "poll_end durationMs=" + padDirectWorkerLastPollDurationMs
                    + " resultCount=" + padDirectWorkerLastPollResultCount
                    + " oldestJobAgeMs=" + padDirectWorkerLastOldestJobAgeMs);
                if (!padDirectWorkerRunning || padDirectWorkerStopRequested) {
                    stopPadDirectAutoWorker(autoStatus, startAutoButton, stopAutoButton, "已停止。", "stopped-during-poll", false);
                    return;
                }
                if (finalJob != null) {
                    autoStatus.setText(formatPadDirectWorkerStatus() + "\n" + finalResult.message);
                } else {
                    autoStatus.setText(formatPadDirectWorkerStatus() + "\n" + finalResult.message);
                }
                if (finalResult.recovering) {
                    long recoveryDelayMs = nextPadDirectRecoveryDelayMs();
                    setPadDirectWorkerState(PadDirectWorkerState.RECOVERING);
                    autoStatus.setText(formatPadDirectWorkerStatus() + "\n" + finalResult.message + "\n" + (recoveryDelayMs / 1000) + " 秒后自动重试。");
                    Log.w(WORKER_TAG, "Worker Recovering delayMs=" + recoveryDelayMs + " reason=" + finalResult.message);
                    schedulePadDirectWorkerTick(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput, recoveryDelayMs);
                    return;
                }
                resetPadDirectRecovery();
                if (finalResult.refreshAfter) {
                    refreshPendingPrintJobs(resultView, jobsList, refreshButton, printerIpInput, printerPortInput, printerTimeoutInput);
                }
                if (finalResult.stopWorker) {
                    padDirectQuickPollsRemaining = 0;
                    padDirectWorkerPendingKick = false;
                    boolean thresholdReached = padDirectWorkerConsecutiveErrors >= PAD_DIRECT_MAX_CONSECUTIVE_ERRORS;
                    String stopReason = thresholdReached ? "watchdog-error-threshold" : finalResult.message;
                    Log.w(WORKER_TAG, "Worker Stopped reason=" + stopReason);
                    stopPadDirectAutoWorker(autoStatus, startAutoButton, stopAutoButton, finalResult.message, stopReason, true);
                    return;
                }
                if (padDirectWorkerPendingKick) {
                    padDirectWorkerPendingKick = false;
                    padDirectQuickPollsRemaining = 2;
                    clearPadDirectWorkerCallbacks();
                    setPadDirectWorkerState(PadDirectWorkerState.WAITING);
                    autoStatus.setText(formatPadDirectWorkerStatus() + "\n当前 job 已结束，立即处理提交订单触发的快速检查。");
                    schedulePadDirectWorkerTick(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput, 0);
                    return;
                }
                long delay;
                if (finalResult.idle && padDirectQuickPollsRemaining > 0) {
                    padDirectQuickPollsRemaining -= 1;
                    if (padDirectQuickPollsRemaining > 0) {
                        delay = PAD_DIRECT_KICK_RETRY_DELAY_MS;
                        autoStatus.setText(formatPadDirectWorkerStatus() + "\n快速检查暂未发现任务，700ms 后再查一次。");
                    } else {
                        delay = 4000;
                        autoStatus.setText(formatPadDirectWorkerStatus() + "\n快速检查窗口结束，回到正常轮询。");
                    }
                } else {
                    if (!finalResult.idle) {
                        padDirectQuickPollsRemaining = 0;
                    }
                    delay = finalResult.conflict ? 500 : (finalResult.idle ? 4000 : 1000);
                }
                if (finalResult.idle) {
                    setPadDirectWorkerState(PadDirectWorkerState.WAITING);
                } else {
                    clearPadDirectCurrentJob();
                    setPadDirectWorkerState(PadDirectWorkerState.WAITING);
                }
                schedulePadDirectWorkerTick(autoStatus, resultView, jobsList, refreshButton, startAutoButton, stopAutoButton, printerIpInput, printerPortInput, printerTimeoutInput, delay);
            });
        }).start();
    }

    private String reportPadDirectFail(Long jobId, String attemptToken, String errorCode, String errorMessage, String rawResult) {
        try {
            long failStartedAt = System.currentTimeMillis();
            JSONObject failRequest = new JSONObject();
            failRequest.put("client_attempt_token", attemptToken);
            failRequest.put("error_code", errorCode == null || errorCode.isBlank() ? "ANDROID_NATIVE_PRINT_FAILED" : errorCode);
            failRequest.put("error_message", truncateForJson(errorMessage, 1000));
            failRequest.put("raw_result", truncateForJson(rawResult, 3000));
            HttpResult failResult = postDeviceJson("/api/v1/printing/jobs/" + jobId + "/fail", failRequest);
            logWorkerDuration("fail_duration_ms", jobId == null ? -1L : jobId, failStartedAt);
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
        Log.d(WORKER_TAG, "Worker API Request: " + method + " " + url);
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

    private boolean isRecoverableApiStep(PadDirectStepException exception) {
        if (exception == null || exception.errorCode == null) {
            return false;
        }
        return "ANDROID_NETWORK_ERROR".equals(exception.errorCode)
            || "ANDROID_API_ERROR".equals(exception.errorCode);
    }

    private String recoverableWorkerMessage(String context, Exception exception) {
        String detail = exception == null ? "" : exception.getClass().getSimpleName() + " - " + exception.getMessage();
        return context + "。网络异常，自动恢复中。" + (detail.isBlank() ? "" : " 技术信息: " + detail);
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
            String nativeCode = result.optString("native_error_code", code);
            String phase = result.optString("phase", "UNKNOWN");
            int bytesWritten = result.optInt("bytes_written", 0);
            String endpoint = result.optString("endpoint", "");
            String exceptionClass = result.optString("exception_class", "");
            String exceptionMessage = result.optString("exception_message", result.optString("message", ""));
            String message = result.optString("message", "");
            return printerErrorMessage(nativeCode)
                + "\n技术信息: native_error_code=" + nativeCode
                + "; phase=" + phase
                + "; bytes_written=" + bytesWritten
                + (endpoint.isBlank() ? "" : "; endpoint=" + endpoint)
                + (exceptionClass.isBlank() ? "" : "; exception_class=" + exceptionClass)
                + (exceptionMessage.isBlank() ? (message.isBlank() ? "" : "; message=" + message) : "; exception_message=" + exceptionMessage);
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
        if ("FLUSH_FAILED".equals(code)) {
            return "发送完成时失败。请检查打印机状态，可能已经出纸。";
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
        padDirectWorkerUserStopped = true;
        setPadDirectAutoPrintEnabled(false);
        padDirectWorkerStopRequested = true;
        padDirectWorkerRunning = false;
        padDirectWorkerInProgress = false;
        padDirectWorkerStoppedForLifecycle = false;
        padDirectWorkerErrorStopped = false;
        activePadDirectWorkerControls = null;
        clearPadDirectWorkerCallbacks();
        updatePadDirectKeepScreenOn();
        setPadDirectWorkerState(PadDirectWorkerState.STOPPED);
        padDirectWorkerLastStopReason = "device-pairing-cleared";
        clearPadDirectCurrentJob();
        Log.i(WORKER_TAG, "Worker Stopped reason=device-pairing-cleared");
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

    private void stopPadDirectWorkerForLifecycle(String reason) {
        if (padDirectWorkerStoppedForLifecycle && !padDirectWorkerRunning) {
            Log.i(WORKER_TAG, "Worker Stopped already pending lifecycle resume reason=" + reason);
            return;
        }
        boolean shouldResume = padDirectWorkerRunning
            && !padDirectWorkerStopRequested
            && activePadDirectWorkerControls != null
            && isPadDirectAutoPrintEnabled();
        if (shouldResume) {
            Log.i(WORKER_TAG, "Worker Stopped reason=" + reason + "; worker will resume when app returns to foreground");
        } else if (padDirectWorkerRunning) {
            Log.i(WORKER_TAG, "Worker Stopped reason=" + reason + "; no automatic resume");
        }
        padDirectWorkerStoppedForLifecycle = shouldResume;
        padDirectWorkerStopRequested = true;
        padDirectWorkerRunning = false;
        padDirectWorkerInProgress = false;
        padDirectWorkerLastStopReason = reason;
        clearPadDirectWorkerCallbacks();
        updatePadDirectKeepScreenOn();
        setPadDirectWorkerState(PadDirectWorkerState.STOPPED);
        updatePadDirectWorkerControls();
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

    private static class PadDirectJobResult {
        private final String message;
        private final boolean refreshAfter;
        private final boolean stopWorker;
        private final boolean conflict;
        private final boolean idle;
        private final boolean recovering;

        private PadDirectJobResult(String message, boolean refreshAfter, boolean stopWorker, boolean conflict, boolean idle, boolean recovering) {
            this.message = message == null ? "" : message;
            this.refreshAfter = refreshAfter;
            this.stopWorker = stopWorker;
            this.conflict = conflict;
            this.idle = idle;
            this.recovering = recovering;
        }

        private static PadDirectJobResult success(String message) {
            return new PadDirectJobResult(message, true, false, false, false, false);
        }

        private static PadDirectJobResult conflict(String message) {
            return new PadDirectJobResult(message, true, false, true, false, false);
        }

        private static PadDirectJobResult idle(String message) {
            return new PadDirectJobResult(message, false, false, false, true, false);
        }

        private static PadDirectJobResult recover(String message) {
            return new PadDirectJobResult(message, false, false, false, false, true);
        }

        private static PadDirectJobResult stop(String message, boolean refreshAfter) {
            return new PadDirectJobResult(message, refreshAfter, true, false, false, false);
        }
    }

    private static class AssignedPrinterEndpoint {
        private final String host;
        private final int port;
        private final int timeoutMs;
        private final String endpoint;
        private final Long printerId;
        private final String printerName;

        private AssignedPrinterEndpoint(String host, int port, int timeoutMs, String endpoint, Long printerId, String printerName) {
            this.host = host;
            this.port = port;
            this.timeoutMs = timeoutMs;
            this.endpoint = endpoint;
            this.printerId = printerId;
            this.printerName = printerName == null ? "" : printerName;
        }
    }

    private static class NativePrintAttemptResult {
        private final boolean success;
        private final String nativeErrorCode;
        private final String phase;
        private final int bytesWritten;
        private final String endpoint;
        private final String exceptionClass;
        private final String exceptionMessage;
        private final String message;
        private final int attemptNumber;
        private String rawResult;

        private NativePrintAttemptResult(
            boolean success,
            String nativeErrorCode,
            String phase,
            int bytesWritten,
            String endpoint,
            String exceptionClass,
            String exceptionMessage,
            String message,
            int attemptNumber,
            String rawResult
        ) {
            this.success = success;
            this.nativeErrorCode = normalizeDiagnostic(nativeErrorCode, success ? "OK" : "UNKNOWN");
            this.phase = normalizeDiagnostic(phase, success ? "DONE" : "UNKNOWN");
            this.bytesWritten = Math.max(bytesWritten, 0);
            this.endpoint = endpoint == null ? "" : endpoint;
            this.exceptionClass = exceptionClass == null ? "" : exceptionClass;
            this.exceptionMessage = exceptionMessage == null ? "" : exceptionMessage;
            this.message = message == null ? "" : message;
            this.attemptNumber = attemptNumber;
            this.rawResult = rawResult == null ? "" : rawResult;
        }

        private static NativePrintAttemptResult from(String rawResult, int attemptNumber) {
            try {
                JSONObject json = new JSONObject(rawResult == null ? "{}" : rawResult);
                return new NativePrintAttemptResult(
                    json.optBoolean("success", false),
                    json.optString("native_error_code", json.optString("error_code", "")),
                    json.optString("phase", ""),
                    json.optInt("bytes_written", 0),
                    json.optString("endpoint", ""),
                    json.optString("exception_class", ""),
                    json.optString("exception_message", ""),
                    json.optString("message", ""),
                    attemptNumber,
                    rawResult
                );
            } catch (Exception exception) {
                return failure(
                    "UNKNOWN",
                    "UNKNOWN",
                    0,
                    "",
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    "Invalid native print result",
                    attemptNumber,
                    rawResult
                );
            }
        }

        private static NativePrintAttemptResult failure(
            String nativeErrorCode,
            String phase,
            int bytesWritten,
            String endpoint,
            String exceptionClass,
            String exceptionMessage,
            String message,
            int attemptNumber,
            String rawResult
        ) {
            return new NativePrintAttemptResult(
                false,
                nativeErrorCode,
                phase,
                bytesWritten,
                endpoint,
                exceptionClass,
                exceptionMessage,
                message,
                attemptNumber,
                rawResult
            );
        }

        private JSONObject toSummaryJson() {
            JSONObject summary = new JSONObject();
            try {
                summary.put("attempt", attemptNumber);
                summary.put("success", success);
                summary.put("native_error_code", nativeErrorCode);
                summary.put("phase", phase);
                summary.put("bytes_written", bytesWritten);
                summary.put("endpoint", endpoint);
                summary.put("exception_class", exceptionClass);
                summary.put("exception_message", exceptionMessage);
                summary.put("message", message);
            } catch (Exception ignored) {
                // Best-effort diagnostic only; never fail printing because diagnostic serialization failed.
            }
            return summary;
        }

        private static String normalizeDiagnostic(String value, String fallback) {
            if (value == null || value.trim().isEmpty()) {
                return fallback;
            }
            return value.trim().toUpperCase(Locale.US);
        }
    }

    private static class PadDirectWorkerControls {
        private final TextView autoStatus;
        private final TextView resultView;
        private final LinearLayout jobsList;
        private final Button refreshButton;
        private final Button startAutoButton;
        private final Button stopAutoButton;
        private final EditText printerIpInput;
        private final EditText printerPortInput;
        private final EditText printerTimeoutInput;

        private PadDirectWorkerControls(
            TextView autoStatus,
            TextView resultView,
            LinearLayout jobsList,
            Button refreshButton,
            Button startAutoButton,
            Button stopAutoButton,
            EditText printerIpInput,
            EditText printerPortInput,
            EditText printerTimeoutInput
        ) {
            this.autoStatus = autoStatus;
            this.resultView = resultView;
            this.jobsList = jobsList;
            this.refreshButton = refreshButton;
            this.startAutoButton = startAutoButton;
            this.stopAutoButton = stopAutoButton;
            this.printerIpInput = printerIpInput;
            this.printerPortInput = printerPortInput;
            this.printerTimeoutInput = printerTimeoutInput;
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
                    .putBoolean(KEY_PAD_DIRECT_AUTO_ENABLED, true)
                    .apply();
                padDirectWorkerUserStopped = false;
                padDirectWorkerErrorStopped = false;
                runOnUiThread(() -> startPadDirectWorkerHeadlessIfPaired("device-paired"));
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
        public String getPrintWorkerStatus() {
            try {
                return jsonSuccess(buildPadDirectWorkerStatusJson());
            } catch (Exception exception) {
                return jsonFailure(exception.getMessage() == null ? "Failed to read print worker status" : exception.getMessage());
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

        @JavascriptInterface
        public String kickPrintWorker(String json) {
            return kickPadDirectWorkerFromWeb(json);
        }

        private Object blankToJsonNull(String value) {
            return value == null || value.isBlank() ? JSONObject.NULL : value;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        padDirectAppForeground = true;
        Log.i(WORKER_TAG, "onStart workerRunning=" + padDirectWorkerRunning + " autoEnabled=" + isPadDirectAutoPrintEnabled());
        resumePadDirectWorkerAfterLifecycleIfNeeded();
        if (!padDirectWorkerStoppedForLifecycle) {
            startPadDirectWorkerHeadlessIfPaired("app-start");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        padDirectAppForeground = true;
        Log.i(WORKER_TAG, "onResume workerRunning=" + padDirectWorkerRunning + " autoEnabled=" + isPadDirectAutoPrintEnabled());
        resumePadDirectWorkerAfterLifecycleIfNeeded();
        if (!padDirectWorkerStoppedForLifecycle) {
            startPadDirectWorkerHeadlessIfPaired("app-resume");
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

    @Override
    protected void onPause() {
        Log.i(WORKER_TAG, "onPause workerRunning=" + padDirectWorkerRunning + " inProgress=" + padDirectWorkerInProgress);
        padDirectAppForeground = false;
        stopPadDirectWorkerForLifecycle("app-pause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i(WORKER_TAG, "onStop workerRunning=" + padDirectWorkerRunning + " stoppedForLifecycle=" + padDirectWorkerStoppedForLifecycle);
        padDirectAppForeground = false;
        stopPadDirectWorkerForLifecycle("app-stopped");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(WORKER_TAG, "onDestroy workerRunning=" + padDirectWorkerRunning);
        padDirectAppForeground = false;
        stopPadDirectWorkerForLifecycle("app-destroyed");
        padDirectWorkerStoppedForLifecycle = false;
        activePadDirectWorkerControls = null;
        super.onDestroy();
    }
}
