package com.restaurant.pad;
import static org.junit.Assert.*;
import org.junit.Test;
public class PrintRequestProofTest {
    @Test public void exactBackendGoldenVectorAndNarrowPaths() throws Exception {
        assertEquals("21898df597d27ff1f9f7d192567b5dce2e2aabb3c030c1c788531d0817be98e7",
            PrintRequestProof.sign("synthetic-token", "10", "1800000000", "/api/v1/orders/9/reprint", "{}", "Bearer synthetic"));
        assertFalse(PrintRequestProof.allowed("GET", "/api/v1/orders/9/reprint"));
        assertFalse(PrintRequestProof.allowed("POST", "/api/v1/auth/login"));
        assertFalse(PrintRequestProof.allowed("POST", "https://evil.invalid/api/v1/orders/9/reprint"));
        assertNotEquals(PrintRequestProof.sign("synthetic-token", "10", "1800000000", "/api/v1/orders/9/reprint", "{}", "Bearer synthetic"),
            PrintRequestProof.sign("synthetic-token", "10", "1800000000", "/api/v1/orders/9/reprint", "{\"changed\":true}", "Bearer synthetic"));
    }
}
