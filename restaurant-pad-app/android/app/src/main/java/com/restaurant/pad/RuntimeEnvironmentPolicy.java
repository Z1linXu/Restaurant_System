package com.restaurant.pad;
import java.net.URI;
import java.util.Locale;
/** Policies for target changes and trusted WebView messages. */
public final class RuntimeEnvironmentPolicy {
    private RuntimeEnvironmentPolicy() {}
    public static String canonicalOrigin(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("https") && !scheme.equals("http")) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) return "";
            int port = uri.getPort(); if (port == 0 || port > 65535) return "";
            boolean defaultPort = port == -1 || (scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80);
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
        } catch (Exception invalid) { return ""; }
    }
    public static String apiOrigin(String webUrl, String apiUrl) {
        String selected = webUrl == null || webUrl.isBlank() ? apiUrl : webUrl;
        String origin = canonicalOrigin(selected);
        return "https://restaurant-pad.local".equals(origin) ? "" : origin;
    }
    public static boolean trustedMessage(String sourceOrigin, boolean mainFrame, String webUrl) {
        String expected = webUrl == null || webUrl.isBlank() ? "https://restaurant-pad.local" : canonicalOrigin(webUrl);
        return mainFrame && !expected.isBlank() && expected.equals(canonicalOrigin(sourceOrigin));
    }
    public static String settingKey(String origin, String key) {
        return key + "|environment:" + (matchesCredentialOrigin(origin, origin) ? origin : "UNCONFIGURED");
    }
    public static boolean mayReplaceBinding(boolean activeJob, boolean nativeBusy, boolean ambiguous) {
        return !activeJob && !nativeBusy && !ambiguous;
    }
    public static boolean matchesCredentialOrigin(String targetOrigin, String credentialOrigin) {
        return !targetOrigin.isBlank() && targetOrigin.equals(credentialOrigin) && targetOrigin.equals(canonicalOrigin(targetOrigin));
    }
}
