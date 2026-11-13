package com.restaurant.pad;

import static org.junit.Assert.*;
import org.junit.Test;

public class MerchantEndpointPolicyTest {
    private static final String ALLO = "https://pos.allonouilles.com";
    @Test public void onlyExactHttpsCloudAndBundledOriginsAreAllowed() {
        assertTrue(MerchantEndpointPolicy.allows(ALLO + "/api/v1/auth/me", ALLO));
        assertTrue(MerchantEndpointPolicy.allows("https://restaurant-pad.local/stores/1/frontdesk", ALLO));
        for (String url : new String[]{"http://pos.allonouilles.com/api/v1/orders", "https://pos.lanzhounoodlesmtl.com", "https://test.lanzhounoodlesmtl.com", "https://staging-pos.lanzhounoodlesmtl.com", "https://pos.allonouilles.com.attacker.invalid", "https://pos.allonouilles.com:444", "https://user@pos.allonouilles.com", "file:///tmp/a", "javascript:alert(1)", "not a url"}) {
            assertFalse(url, MerchantEndpointPolicy.allows(url, ALLO));
        }
    }
    @Test public void existingConfigurableBuildIsUnchanged() {
        assertTrue(MerchantEndpointPolicy.allows("http://192.168.1.5:5173", ""));
    }
}
