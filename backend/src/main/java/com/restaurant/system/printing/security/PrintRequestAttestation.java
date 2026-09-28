package com.restaurant.system.printing.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class PrintRequestAttestation {
    private PrintRequestAttestation() {}
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    public static String sign(String tokenHash, String device, String timestamp, String path, byte[] body, String authorization) {
        try {
            String canonical = "PRINT_ORIGIN_V1\n" + device + "\n" + timestamp + "\nPOST\n" + path + "\n"
                + sha256(body) + "\n" + sha256(authorization.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            // StoreDeviceService persists SHA-256 as Base64, not hex.
            byte[] key = Base64.getDecoder().decode(tokenHash);
            if (key.length != 32) throw new IllegalArgumentException("Invalid device hash length");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalArgumentException("Invalid print attestation", ex); }
    }
    public static boolean target(String method, String path) {
        return "POST".equals(method) && (path.matches("/api/v1/stores/[0-9]+/orders/idempotent-submit")
            || path.matches("/api/v1/orders/[0-9]+/(submit|updates|reprint)")
            || path.matches("/api/v1/admin/printing/jobs/[0-9]+/reprint"));
    }
}
