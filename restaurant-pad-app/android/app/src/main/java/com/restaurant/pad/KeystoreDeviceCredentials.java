package com.restaurant.pad;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.json.JSONObject;
/** Never reads, adopts or deletes legacy unbound plaintext preferences. */
final class KeystoreDeviceCredentials {
    private static final String ALIAS = "restaurant_pad_device_credentials_v2";
    private final SharedPreferences preferences;
    KeystoreDeviceCredentials(SharedPreferences preferences) { this.preferences = preferences; }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    private String storageKey(String origin) throws Exception {
        if (!RuntimeEnvironmentPolicy.matchesCredentialOrigin(origin, origin)) throw new IllegalArgumentException("Invalid credential origin");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(origin.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(); for (byte value : digest) hex.append(String.format("%02x", value & 255));
        return "device_credentials_v2:" + hex;
    }
    synchronized JSONObject read(String origin) {
        try {
            String envelope = preferences.getString(storageKey(origin), "");
            if (envelope == null || envelope.isBlank()) return null;
            JSONObject record = new JSONObject(CredentialCipher.open(key(), origin, envelope));
            return RuntimeEnvironmentPolicy.matchesCredentialOrigin(origin, record.optString("api_origin")) ? record : null;
        } catch (Exception unavailable) { return null; } // Retain bytes; never fall back to plaintext or another target.
    }
    synchronized void save(String origin, JSONObject record) throws Exception {
        JSONObject copy = new JSONObject(record.toString()); copy.put("api_origin", origin);
        String envelope = CredentialCipher.seal(key(), origin, copy.toString());
        if (!preferences.edit().putString(storageKey(origin), envelope).commit()) throw new IllegalStateException("Unable to save device credentials");
    }
    synchronized void clear(String origin) throws Exception {
        if (!preferences.edit().remove(storageKey(origin)).commit()) throw new IllegalStateException("Unable to clear device credentials");
    }
}
