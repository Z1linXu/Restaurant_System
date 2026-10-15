package com.restaurant.pad;
import java.net.HttpURLConnection;
import java.net.URL;
/** Construct an immutable origin/credential pair before opening a connection. */
final class DeviceApiConnection {
    static HttpURLConnection open(String origin, String credentialOrigin, String deviceId, String token, String path, String method) throws Exception {
        if (!RuntimeEnvironmentPolicy.matchesCredentialOrigin(origin, credentialOrigin)
            || deviceId == null || deviceId.isBlank() || token == null || token.isBlank()
            || path == null || !path.startsWith("/api/v1/") || path.contains("\\") || path.contains("#")) {
            throw new IllegalStateException("Device credential environment mismatch");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(origin + path).openConnection();
        connection.setInstanceFollowRedirects(false); connection.setRequestMethod(method);
        connection.setConnectTimeout(5000); connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Device-Id", deviceId); connection.setRequestProperty("X-Device-Token", token);
        return connection;
    }
}
