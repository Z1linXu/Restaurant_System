package com.restaurant.system.printing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.printing.security.*;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.*;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class PrintOriginFilterTest {
    final StoreDeviceRepository devices = mock(StoreDeviceRepository.class);
    final StoreRepository stores = mock(StoreRepository.class);
    final StoreDevice device = new StoreDevice();
    final Store store = new Store();
    final PrintOriginFilter filter = new PrintOriginFilter(devices, stores, mock(OrderRepository.class), mock(PrintJobRepository.class), new ObjectMapper());
    final String path = "/api/v1/stores/1/orders/idempotent-submit";
    final byte[] body = "{\"idempotency_key\":\"test-1234\"}".getBytes(StandardCharsets.UTF_8);
    PrintOriginFilterTest() {
        store.id = 1L; store.organization_id = 2L; store.printing_mode = "PAD_DIRECT";
        device.id = 10L; device.storeId = 1L; device.organizationId = 2L; device.isActive = true; device.status = "ACTIVE";
        device.deviceTokenHash = PrintRequestAttestation.sha256("synthetic-not-a-real-token".getBytes(StandardCharsets.UTF_8));
        when(stores.findById(1L)).thenReturn(Optional.of(store)); when(devices.findById(10L)).thenReturn(Optional.of(device));
    }
    MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", path); request.setContent(body);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        request.addHeader("Authorization", "Bearer synthetic"); request.addHeader("X-Print-Device-Id", "10");
        request.addHeader("X-Print-Timestamp", timestamp);
        request.addHeader("X-Print-Signature", PrintRequestAttestation.sign(device.deviceTokenHash, "10", timestamp, path, body, "Bearer synthetic"));
        return request;
    }
    @Test void validProofKeepsHttpBodyAndExposesOnlyVerifiedScope() throws Exception {
        var request = request(); var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (r, s) -> {
            assertThat(r.getInputStream().readAllBytes()).isEqualTo(body);
            assertThat(r.getAttribute(PrintOriginContext.ATTRIBUTE)).isEqualTo(new PrintOriginContext.Origin(10L, 1L, 2L));
        });
        assertThat(response.getStatus()).isEqualTo(200);
    }
    @Test void nativeAndBackendShareExactGoldenVector() {
        assertThat(PrintRequestAttestation.sign(PrintRequestAttestation.sha256("synthetic-token".getBytes(StandardCharsets.UTF_8)),
            "10", "1800000000", "/api/v1/orders/9/reprint", "{}".getBytes(StandardCharsets.UTF_8), "Bearer synthetic"))
            .isEqualTo("21898df597d27ff1f9f7d192567b5dce2e2aabb3c030c1c788531d0817be98e7");
    }
    @Test void tamperedBodyWrongOrganizationRevokedAndExpiredProofFailClosed() throws Exception {
        var changed = request(); changed.setContent("{}".getBytes(StandardCharsets.UTF_8)); assertDenied(changed);
        device.organizationId = 3L; assertDenied(request()); device.organizationId = 2L;
        device.status = "REVOKED"; assertDenied(request()); device.status = "ACTIVE";
        device.storeId = 9L; assertDenied(request()); device.storeId = 1L;
        device.isActive = false; assertDenied(request()); device.isActive = true;
        var expired = request(); expired.removeHeader("X-Print-Timestamp"); expired.addHeader("X-Print-Timestamp", "1"); assertDenied(expired);
        var noProof = new MockHttpServletRequest("POST", path); assertDenied(noProof);
    }
    void assertDenied(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (r, s) -> { throw new AssertionError("Unverified request reached controller"); });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).doesNotContain(device.deviceTokenHash).contains("PRINT_ORIGIN_INVALID");
    }
}
