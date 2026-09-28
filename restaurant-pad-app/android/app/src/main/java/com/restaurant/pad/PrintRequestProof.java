package com.restaurant.pad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class PrintRequestProof {
    static boolean allowed(String method, String path) {
        return "POST".equals(method) && (path.matches("/api/v1/stores/[0-9]+/orders/idempotent-submit")
            || path.matches("/api/v1/orders/[0-9]+/(submit|updates|reprint)")
            || path.matches("/api/v1/admin/printing/jobs/[0-9]+/reprint"));
    }
    static String hex(byte[] data) {
        StringBuilder result = new StringBuilder();
        for (byte value : data) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    static byte[] digest(String text) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    }
    static String sign(String token, String device, String timestamp, String path, String body, String authorization) throws Exception {
        if (!allowed("POST", path) || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException("Unsupported proof request");
        String canonical = "PRINT_ORIGIN_V1\n" + device + "\n" + timestamp + "\nPOST\n" + path + "\n"
            + hex(digest(body)) + "\n" + hex(digest(authorization));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(digest(token), "HmacSHA256"));
        return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
