package com.restaurant.system.printing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.printing.entity.PrinterConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CloudPrintingGuardTest {

    @Test
    void cloudProfileBlocksPrivateAndLocalPrinterHosts() {
        CloudPrintingGuard guard = guard("cloud");

        assertBlocked(guard, "192.168.2.200");
        assertBlocked(guard, "10.12.0.5");
        assertBlocked(guard, "172.16.0.1");
        assertBlocked(guard, "172.31.255.250");
        assertBlocked(guard, "127.0.0.1");
        assertBlocked(guard, "localhost");
        assertBlocked(guard, "0.0.0.0");
        assertBlocked(guard, "169.254.10.20");
        assertBlocked(guard, "::1");
        assertBlocked(guard, "fe80::1");
    }

    @Test
    void cloudProfileAllowsPublicLiteralIpAndDomainNames() {
        CloudPrintingGuard guard = guard("production");

        assertAllowed(guard, "8.8.8.8");
        assertAllowed(guard, "172.32.0.1");
        assertAllowed(guard, "printer.example.com");
    }

    @Test
    void localDevAndPilotProfilesAllowLanPrinterHosts() {
        assertAllowed(guard("local"), "192.168.2.200");
        assertAllowed(guard("dev"), "10.12.0.5");
        assertAllowed(guard("pilot"), "172.16.0.1");
    }

    private void assertBlocked(CloudPrintingGuard guard, String host) {
        assertTrue(guard.blockedBackendTcpMessage(printer(host)).isPresent(), host + " should be blocked");
    }

    private void assertAllowed(CloudPrintingGuard guard, String host) {
        assertFalse(guard.blockedBackendTcpMessage(printer(host)).isPresent(), host + " should be allowed");
    }

    private CloudPrintingGuard guard(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new CloudPrintingGuard(environment);
    }

    private PrinterConfig printer(String host) {
        PrinterConfig printer = new PrinterConfig();
        printer.ip_address = host;
        printer.port = 9100;
        return printer;
    }
}
