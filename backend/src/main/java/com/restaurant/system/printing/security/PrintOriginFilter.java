package com.restaurant.system.printing.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.repository.StoreDeviceRepository;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Narrow request proof. React still owns HTTP, offline outbox and idempotency. */
@Component
public class PrintOriginFilter extends OncePerRequestFilter {
    private final StoreDeviceRepository devices;
    private final StoreRepository stores;
    private final OrderRepository orders;
    private final PrintJobRepository jobs;
    private final ObjectMapper json;
    public PrintOriginFilter(StoreDeviceRepository devices, StoreRepository stores, OrderRepository orders,
                             PrintJobRepository jobs, ObjectMapper json) {
        this.devices = devices; this.stores = stores; this.orders = orders; this.jobs = jobs; this.json = json;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PrintRequestAttestation.target(request.getMethod(), request.getRequestURI());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        HttpServletRequest verified = request;
        try {
            String path = request.getRequestURI();
            String[] parts = path.split("/");
            Long storeId;
            if (path.startsWith("/api/v1/stores/")) storeId = Long.valueOf(parts[4]);
            else if (path.startsWith("/api/v1/orders/")) {
                var order = orders.findExistingById(Long.valueOf(parts[4]));
                if (order == null) throw new IllegalArgumentException();
                storeId = order.store_id;
            } else storeId = jobs.findById(Long.valueOf(parts[6])).orElseThrow().store_id;
            var store = stores.findById(storeId).orElseThrow();
            String id = request.getHeader("X-Print-Device-Id");
            if (id != null || "PAD_DIRECT".equals(store.printing_mode)) {
            String timestamp = request.getHeader("X-Print-Timestamp");
            String signature = request.getHeader("X-Print-Signature");
            String authorization = request.getHeader("Authorization");
            if (id == null || timestamp == null || signature == null || authorization == null
                || !authorization.startsWith("Bearer ") || !signature.matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
            long signedAt = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (signedAt < now - 90 || signedAt > now + 15) throw new IllegalArgumentException();
            var device = devices.findById(Long.valueOf(id)).orElseThrow();
            if (!Boolean.TRUE.equals(device.isActive) || !"ACTIVE".equals(device.status)
                || !Objects.equals(storeId, device.storeId) || !Objects.equals(store.organization_id, device.organizationId))
                throw new IllegalArgumentException();
            byte[] body = request.getInputStream().readNBytes(2 * 1024 * 1024 + 1);
            if (body.length > 2 * 1024 * 1024) throw new IllegalArgumentException();
            String expected = PrintRequestAttestation.sign(device.deviceTokenHash, id, timestamp, path, body, authorization);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException();
            request.setAttribute(PrintOriginContext.ATTRIBUTE, new PrintOriginContext.Origin(device.id, storeId, store.organization_id));
            verified = new HttpServletRequestWrapper(request) {
                @Override public ServletInputStream getInputStream() {
                    ByteArrayInputStream input = new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        public int read() { return input.read(); }
                        public boolean isFinished() { return input.available() == 0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
                @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
            };
            }
        } catch (RuntimeException ex) {
            response.setStatus(403); response.setContentType("application/json");
            json.writeValue(response.getOutputStream(), ApiResponse.failure("PRINT_ORIGIN_INVALID", "有效的同店 Pad 请求证明必需 / Valid paired Pad request proof required"));
            return;
        }
        chain.doFilter(verified, response);
    }
}
