package com.restaurant.system.common.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SystemHealthControllerTest {

    @Test
    void healthReturnsSmallReachabilityPayload() {
        var response = new SystemHealthController().health();

        assertTrue(response.isSuccess());
        assertEquals("UP", response.getData().get("status"));
        assertTrue(response.getData().containsKey("timestamp"));
    }
}
