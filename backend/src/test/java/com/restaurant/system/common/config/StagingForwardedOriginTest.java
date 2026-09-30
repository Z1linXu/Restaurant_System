package com.restaurant.system.common.config;

import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.Arrays;
import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StagingForwardedOriginTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "server.forward-headers-strategy=NATIVE",
        "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1",
        "server.tomcat.remoteip.remote-ip-header=X-Forwarded-For",
        "server.tomcat.remoteip.protocol-header=X-Forwarded-Proto",
        "server.tomcat.remoteip.host-header=X-Forwarded-Host",
        "server.tomcat.remoteip.port-header=X-Forwarded-Port"
    })
class StagingForwardedOriginTest {
    @LocalServerPort int port;
    @Autowired ServletWebServerApplicationContext context;
    private static final String DOMAIN = "staging-pos.lanzhounoodlesmtl.com";

    @Test void stagingHttpsOriginUsesExternalSchemeHostAndPort() throws Exception {
        String response = request("https://" + DOMAIN, true);
        assertThat(response).startsWith("HTTP/1.1 200");
        assertThat(response).contains("https|" + DOMAIN + "|443|203.0.113.42");
    }
    @Test void invalidOriginIsStillRejected() throws Exception {
        assertThat(request("https://attacker.invalid", true)).startsWith("HTTP/1.1 403");
    }
    @Test void androidBundledOriginRemainsAllowed() throws Exception {
        assertThat(request("https://restaurant-pad.local", true)).startsWith("HTTP/1.1 200");
    }
    @Test void localDevelopmentOriginRemainsAllowed() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "http://127.0.0.1:5173", "http://192.168.1.9:5173"}) {
            assertThat(request(origin, true)).startsWith("HTTP/1.1 200");
        }
    }
    @Test void missingForwardingDoesNotSilentlyAllowHttpsOrigin() throws Exception {
        assertThat(request("https://" + DOMAIN, false)).startsWith("HTTP/1.1 403");
    }
    @Test void originWithDifferentPortIsRejected() throws Exception {
        assertThat(request("https://" + DOMAIN + ":444", true)).startsWith("HTTP/1.1 403");
    }
    @Test void untrustedProxyCannotAssertExternalHttps() throws Exception {
        var server = (TomcatWebServer) context.getWebServer();
        var valve = (RemoteIpValve) Arrays.stream(server.getTomcat().getEngine().getPipeline().getValves())
            .filter(RemoteIpValve.class::isInstance).findFirst().orElseThrow();
        String original = valve.getInternalProxies();
        try {
            valve.setInternalProxies("192\\.0\\.2\\.10");
            assertThat(request("https://" + DOMAIN, true)).startsWith("HTTP/1.1 403");
        } finally {
            valve.setInternalProxies(original);
        }
    }
    private String request(String origin, boolean forwarded) throws Exception {
        return request(origin, forwarded, "127.0.0.1");
    }
    private String request(String origin, boolean forwarded, String source) throws Exception {
        try (Socket socket = new Socket()) {
            socket.bind(new InetSocketAddress(source, 0));
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(5000);
            var writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII);
            writer.write("GET /api/ingress-test HTTP/1.1\r\nHost: internal.invalid\r\nConnection: close\r\nOrigin: " + origin + "\r\n");
            if (forwarded) writer.write("X-Forwarded-Proto: https\r\nX-Forwarded-Host: " + DOMAIN + "\r\nX-Forwarded-Port: 443\r\nX-Forwarded-For: 203.0.113.42\r\n");
            writer.write("\r\n");
            writer.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({RestCorsConfig.class, ProbeController.class})
    static class TestApplication {}
    @RestController
    static class ProbeController {
        @GetMapping("/api/ingress-test") String probe(HttpServletRequest request) {
            return request.getScheme() + "|" + request.getServerName() + "|" + request.getServerPort() + "|" + request.getRemoteAddr();
        }
    }
}
