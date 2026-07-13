package com.restaurant.pad;

import android.content.Context;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;
import java.util.function.Supplier;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONObject;

public class FrontendAssetPathHandler implements WebViewAssetLoader.PathHandler {
    private static final String WEB_ROOT = "web/";
    private final Context context;
    private final Supplier<String> apiBaseSupplier;
    private final Supplier<String> storeIdSupplier;
    private final Supplier<String> appVersionSupplier;

    public FrontendAssetPathHandler(
        Context context,
        Supplier<String> apiBaseSupplier,
        Supplier<String> storeIdSupplier,
        Supplier<String> appVersionSupplier
    ) {
        this.context = context.getApplicationContext();
        this.apiBaseSupplier = apiBaseSupplier;
        this.storeIdSupplier = storeIdSupplier;
        this.appVersionSupplier = appVersionSupplier;
    }

    @Override
    public WebResourceResponse handle(String path) {
        String assetPath = resolveAssetPath(path);
        try {
            if (assetPath.endsWith("index.html")) {
                return htmlResponse(injectRuntimeConfig(readAssetText(assetPath)));
            }
            return streamResponse(assetPath, context.getAssets().open(assetPath));
        } catch (IOException missingAsset) {
            try {
                return htmlResponse(injectRuntimeConfig(readAssetText(WEB_ROOT + "index.html")));
            } catch (IOException missingIndex) {
                return htmlResponse("<!doctype html><h1>Restaurant Pad</h1><p>Missing bundled web/index.html.</p>");
            }
        }
    }

    private String resolveAssetPath(String rawPath) {
        String path = rawPath == null || rawPath.isBlank() || "/".equals(rawPath) ? "/index.html" : rawPath;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.contains(".") || path.endsWith("/")) {
            path = "index.html";
        }
        return WEB_ROOT + path;
    }

    private String readAssetText(String assetPath) throws IOException {
        try (InputStream stream = context.getAssets().open(assetPath);
             Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private WebResourceResponse htmlResponse(String html) {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        return new WebResourceResponse(
            "text/html",
            StandardCharsets.UTF_8.name(),
            new ByteArrayInputStream(bytes)
        );
    }

    private WebResourceResponse streamResponse(String assetPath, InputStream stream) {
        return new WebResourceResponse(resolveMimeType(assetPath), null, stream);
    }

    private String resolveMimeType(String assetPath) {
        String guessed = URLConnection.guessContentTypeFromName(assetPath);
        if (guessed != null) {
            return guessed;
        }
        String lower = assetPath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".js")) {
            return "text/javascript";
        }
        if (lower.endsWith(".css")) {
            return "text/css";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        return "application/octet-stream";
    }

    private String injectRuntimeConfig(String html) {
        String suppliedApiBase = apiBaseSupplier.get();
        String apiBase = suppliedApiBase == null ? "" : suppliedApiBase.trim();
        String suppliedStoreId = storeIdSupplier.get();
        String storeId = suppliedStoreId != null && suppliedStoreId.trim().matches("\\d+")
            ? suppliedStoreId.trim()
            : "null";
        String suppliedAppVersion = appVersionSupplier.get();
        String appVersion = suppliedAppVersion == null ? "" : suppliedAppVersion.trim();
        JSONObject buildInfo = readBuildInfo();
        String buildVersion = buildInfo.optString("buildVersion", "unknown");
        String manifestHash = buildInfo.optString("assetManifestSha256", "unknown");
        int offlineSchemaVersion = buildInfo.optInt("offlineDatabaseSchemaVersion", -1);
        String script = "\n<script>\n" +
            "(function(){\n" +
            "  var apiBase = '" + escapeJs(apiBase) + "'.replace(/\\/$/, '');\n" +
            "  window.__RESTAURANT_API_BASE_URL__ = apiBase;\n" +
            "  window.__RESTAURANT_WS_BASE_URL__ = apiBase.replace(/^http/i, 'ws');\n" +
            "  window.__RESTAURANT_APP_CONFIG__ = {\n" +
            "    mode: 'BUNDLED_ASSETS',\n" +
            "    appVersion: '" + escapeJs(appVersion) + "',\n" +
            "    buildVersion: '" + escapeJs(buildVersion) + "',\n" +
            "    assetManifestSha256: '" + escapeJs(manifestHash) + "',\n" +
            "    offlineDatabaseSchemaVersion: " + offlineSchemaVersion + ",\n" +
            "    storeId: " + storeId + "\n" +
            "  };\n" +
            "  var originalFetch = window.fetch && window.fetch.bind(window);\n" +
            "  if (originalFetch) {\n" +
            "    window.fetch = function(input, init) {\n" +
            "      if (apiBase && typeof input === 'string' && input.indexOf('/api/') === 0) {\n" +
            "        input = apiBase + input;\n" +
            "      } else if (apiBase && input && input.url && input.url.indexOf(location.origin + '/api/') === 0) {\n" +
            "        input = new Request(apiBase + input.url.substring(location.origin.length), input);\n" +
            "      }\n" +
            "      return originalFetch(input, init);\n" +
            "    };\n" +
            "  }\n" +
            "  var OriginalWebSocket = window.WebSocket;\n" +
            "  if (OriginalWebSocket) {\n" +
            "    window.WebSocket = function(url, protocols) {\n" +
            "      if (apiBase && typeof url === 'string' && url.indexOf('/ws') === 0) {\n" +
            "        url = window.__RESTAURANT_WS_BASE_URL__ + url;\n" +
            "      }\n" +
            "      return protocols ? new OriginalWebSocket(url, protocols) : new OriginalWebSocket(url);\n" +
            "    };\n" +
            "    window.WebSocket.prototype = OriginalWebSocket.prototype;\n" +
            "  }\n" +
            "})();\n" +
            "</script>\n";
        if (html.contains("</head>")) {
            return html.replace("</head>", script + "</head>");
        }
        return script + html;
    }

    private JSONObject readBuildInfo() {
        try {
            return new JSONObject(readAssetText(WEB_ROOT + "build-info.json"));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private String escapeJs(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}
