package com.restaurant.system.printing;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.entity.PrinterConfig;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CloudPrintingGuard {

    public static final String ERROR_CODE = "CLOUD_PRIVATE_PRINTER_BLOCKED";
    public static final String ERROR_MESSAGE = "Cloud server cannot directly connect to private LAN printer. "
        + "Use PAD_DIRECT, MOCK, DISABLED, or a local print bridge.";

    private static final Set<String> STRICT_CLOUD_PROFILES = Set.of("cloud", "prod", "production");

    private final Environment environment;

    public CloudPrintingGuard(Environment environment) {
        this.environment = environment;
    }

    public boolean isStrictCloudProfile() {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
            .map(CloudPrintingGuard::normalize)
            .anyMatch(STRICT_CLOUD_PROFILES::contains);
    }

    public Optional<String> blockedBackendTcpMessage(PrinterConfig printerConfig) {
        if (!isStrictCloudProfile() || printerConfig == null || !isBlockedBackendHost(printerConfig.ip_address)) {
            return Optional.empty();
        }
        return Optional.of(ERROR_MESSAGE + " Blocked printer endpoint: " + endpoint(printerConfig));
    }

    public void assertBackendTcpAllowed(PrinterConfig printerConfig) {
        blockedBackendTcpMessage(printerConfig).ifPresent(message -> {
            throw new BusinessException(message);
        });
    }

    public static boolean isBlockedBackendHost(String host) {
        String normalizedHost = normalizeHost(host);
        if (normalizedHost.isBlank()) {
            return false;
        }
        if ("localhost".equals(normalizedHost) || "localhost.".equals(normalizedHost)) {
            return true;
        }
        if (isBlockedIpv4(normalizedHost)) {
            return true;
        }
        return isBlockedIpv6Literal(normalizedHost);
    }

    private static boolean isBlockedIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int index = 0; index < parts.length; index++) {
            if (!parts[index].matches("\\d{1,3}")) {
                return false;
            }
            octets[index] = Integer.parseInt(parts[index]);
            if (octets[index] < 0 || octets[index] > 255) {
                return false;
            }
        }
        int first = octets[0];
        int second = octets[1];
        return first == 0
            || first == 10
            || first == 127
            || (first == 172 && second >= 16 && second <= 31)
            || (first == 192 && second == 168)
            || (first == 169 && second == 254);
    }

    private static boolean isBlockedIpv6Literal(String host) {
        if (!host.contains(":")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            return address instanceof Inet6Address
                && (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String endpoint(PrinterConfig printerConfig) {
        String host = printerConfig.ip_address == null ? "" : printerConfig.ip_address;
        Integer port = printerConfig.port == null ? 9100 : printerConfig.port;
        return host + ":" + port;
    }

    private static String normalizeHost(String host) {
        String normalized = normalize(host);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
