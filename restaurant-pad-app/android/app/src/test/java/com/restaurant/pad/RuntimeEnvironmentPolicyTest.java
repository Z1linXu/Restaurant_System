package com.restaurant.pad;
import static org.junit.Assert.*;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.Test;
public class RuntimeEnvironmentPolicyTest {
    @Test public void originIdentityDistinguishesSchemePortAndServerButNormalizesDefaults() {
        assertEquals("https://a.invalid", RuntimeEnvironmentPolicy.canonicalOrigin("https://A.invalid:443/pos"));
        assertFalse(RuntimeEnvironmentPolicy.matchesCredentialOrigin("https://b.invalid", "https://a.invalid"));
        assertFalse(RuntimeEnvironmentPolicy.matchesCredentialOrigin("http://a.invalid", "https://a.invalid"));
        assertFalse(RuntimeEnvironmentPolicy.matchesCredentialOrigin("https://a.invalid:8443", "https://a.invalid"));
        for (String invalid : new String[]{"javascript:alert(1)", "file:///tmp/app", "https://user:secret@a.invalid", "https://a.invalid?secret=x", "https://a.invalid:0"}) assertEquals("", RuntimeEnvironmentPolicy.canonicalOrigin(invalid));
        assertEquals("https://web.invalid", RuntimeEnvironmentPolicy.apiOrigin("https://web.invalid/stores/1", "https://api.invalid"));
        assertEquals("https://api.invalid", RuntimeEnvironmentPolicy.apiOrigin("", "https://api.invalid/"));
        assertEquals("", RuntimeEnvironmentPolicy.apiOrigin("", ""));
    }
    @Test public void onlyTheConfiguredMainFrameMayUseDeviceMessages() {
        assertTrue(RuntimeEnvironmentPolicy.trustedMessage("https://restaurant-pad.local", true, ""));
        assertFalse(RuntimeEnvironmentPolicy.trustedMessage("https://restaurant-pad.local", false, ""));
        assertFalse(RuntimeEnvironmentPolicy.trustedMessage("https://other.invalid", true, ""));
        assertFalse(RuntimeEnvironmentPolicy.trustedMessage("https://restaurant-pad.local:8443", true, ""));
        assertFalse(RuntimeEnvironmentPolicy.trustedMessage("http://restaurant-pad.local", true, ""));
        assertTrue(RuntimeEnvironmentPolicy.trustedMessage("https://web.invalid", true, "https://web.invalid/stores/1"));
        assertFalse(RuntimeEnvironmentPolicy.trustedMessage("https://web.invalid", false, "https://web.invalid/stores/1"));
    }
    @Test public void activeOrAmbiguousPrintCannotLoseItsBinding() {
        assertTrue(RuntimeEnvironmentPolicy.mayReplaceBinding(false, false, false));
        for (int mask = 1; mask < 8; mask++) assertFalse(RuntimeEnvironmentPolicy.mayReplaceBinding((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0));
    }
    @Test public void localPrinterTestSettingsRemainWithTheirOriginalServer() {
        assertNotEquals(RuntimeEnvironmentPolicy.settingKey("https://a.invalid", "printer_test_ip"),
            RuntimeEnvironmentPolicy.settingKey("https://b.invalid", "printer_test_ip"));
        assertNotEquals("printer_test_ip", RuntimeEnvironmentPolicy.settingKey("", "printer_test_ip"));
    }
    @Test public void encryptedEnvelopeCannotBeOpenedForAnotherOriginOrKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256); SecretKey key = generator.generateKey();
        String secret = "synthetic-device-token-not-a-real-credential";
        String sealed = CredentialCipher.seal(key, "https://a.invalid", secret);
        assertFalse(sealed.contains(secret)); assertEquals(secret, CredentialCipher.open(key, "https://a.invalid", sealed));
        assertThrows(Exception.class, () -> CredentialCipher.open(key, "https://b.invalid", sealed));
        assertThrows(Exception.class, () -> CredentialCipher.open(generator.generateKey(), "https://a.invalid", sealed));
        assertThrows(Exception.class, () -> CredentialCipher.open(key, "https://a.invalid", "corrupt"));
    }
}
