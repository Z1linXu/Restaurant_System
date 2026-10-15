package com.restaurant.pad;

import static org.junit.Assert.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class ManualPendingJobsRequestTest {
    @Test public void manualPendingListUsesBoundCredentialsAndCannotFollowRedirectToAnotherServer() throws Exception {
        try (ServerSocket a = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
             ServerSocket b = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            a.setSoTimeout(2000); b.setSoTimeout(200);
            String originA = "http://127.0.0.1:" + a.getLocalPort(), originB = "http://127.0.0.1:" + b.getLocalPort();
            AtomicReference<String> requestLine = new AtomicReference<>(), observedToken = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread responder = new Thread(() -> {
                try (Socket socket = a.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    requestLine.set(reader.readLine());
                    String line; while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase().startsWith("x-device-token:")) observedToken.set(line.substring(line.indexOf(':') + 1).trim());
                    }
                    socket.getOutputStream().write(("HTTP/1.1 307 Temporary Redirect\r\nLocation: " + originB
                        + "/api/v1/stores/3/printing/jobs/pending\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                } catch (Throwable error) { failure.set(error); }
            });
            responder.start();
            assertThrows(IllegalStateException.class, () -> new ManualPendingJobsRequest(originB, originA, "7", "synthetic-A", "3", 1).open());
            HttpURLConnection connection = new ManualPendingJobsRequest(originA, originA, "7", "synthetic-A", "3", 1).open();
            try { assertEquals(307, connection.getResponseCode()); } finally { connection.disconnect(); }
            responder.join(2500); assertFalse(responder.isAlive()); assertNull(failure.get());
            assertEquals("GET /api/v1/stores/3/printing/jobs/pending?limit=25 HTTP/1.1", requestLine.get());
            assertEquals("synthetic-A", observedToken.get()); assertThrows(SocketTimeoutException.class, b::accept);
        }
    }
    @Test public void oldResponseIsRejectedAfterTargetPairingOrLifecycleGenerationChanges() {
        ManualPendingJobsRequest request = new ManualPendingJobsRequest("https://a.invalid", "https://a.invalid", "7", "synthetic-A", "3", 4);
        assertTrue(request.isCurrent(4, "https://a.invalid", "7"));
        assertFalse(request.isCurrent(5, "https://a.invalid", "7"));
        assertFalse(request.isCurrent(4, "https://b.invalid", "7"));
        assertFalse(request.isCurrent(4, "https://a.invalid", "8"));
        assertTrue(ManualPendingJobsRequest.canStart(false, false, false, false));
        for (int mask = 1; mask < 16; mask++) assertFalse(ManualPendingJobsRequest.canStart((mask & 1) != 0,
            (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0));
    }
}
