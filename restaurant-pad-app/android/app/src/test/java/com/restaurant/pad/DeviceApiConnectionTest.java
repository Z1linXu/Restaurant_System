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
public class DeviceApiConnectionTest {
    @Test public void syntheticServersNeverReceiveAnotherOriginsTokenAndRedirectCannotForwardIt() throws Exception {
        try (ServerSocket a = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
             ServerSocket b = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            a.setSoTimeout(2000); b.setSoTimeout(200);
            String originA = "http://127.0.0.1:" + a.getLocalPort(), originB = "http://127.0.0.1:" + b.getLocalPort();
            AtomicReference<String> observed = new AtomicReference<>(); AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread responder = new Thread(() -> {
                try (Socket socket = a.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String line; while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase().startsWith("x-device-token:")) observed.set(line.substring(line.indexOf(':') + 1).trim());
                    }
                    socket.getOutputStream().write(("HTTP/1.1 307 Temporary Redirect\r\nLocation: " + originB + "/api/v1/test\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                } catch (Throwable error) { failure.set(error); }
            });
            responder.start();
            assertThrows(IllegalStateException.class, () -> DeviceApiConnection.open(originB, originA, "7", "synthetic-A", "/api/v1/test", "GET"));
            HttpURLConnection request = DeviceApiConnection.open(originA, originA, "7", "synthetic-A", "/api/v1/test", "GET");
            try { assertEquals(307, request.getResponseCode()); } finally { request.disconnect(); }
            responder.join(2500); assertFalse(responder.isAlive()); assertNull(failure.get());
            assertEquals("synthetic-A", observed.get()); assertThrows(SocketTimeoutException.class, b::accept);
        }
    }
}
