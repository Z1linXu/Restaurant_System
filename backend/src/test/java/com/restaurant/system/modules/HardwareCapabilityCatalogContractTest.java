package com.restaurant.system.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class HardwareCapabilityCatalogContractTest {

    private final HardwareCapabilityCatalogDefinition catalog = new ModuleContractLoader().loadHardwareCatalog();

    @Test
    void currentHardwareCapabilityCatalogContainsOnlyReviewedNeeds() {
        assertEquals(
            Set.of(
                HardwareCapabilityKeys.TOUCH_CLIENT,
                HardwareCapabilityKeys.PRINT_GRAB,
                HardwareCapabilityKeys.PRINT_FRONTDESK_RECEIPT,
                HardwareCapabilityKeys.PRINT_HOT_KITCHEN,
                HardwareCapabilityKeys.PAD_DIRECT_PRINT_CLIENT,
                HardwareCapabilityKeys.PAD_DEVICE,
                HardwareCapabilityKeys.DEVICE_ENROLLMENT,
                HardwareCapabilityKeys.KDS_DISPLAY_CLIENT
            ),
            catalog.canonicalCapabilityKeys()
        );
    }

    @Test
    void legacyPrintingTopologyAliasExpandsToCanonicalLogicalPrintCapabilities() {
        assertEquals(
            Set.of(
                HardwareCapabilityKeys.PRINT_GRAB,
                HardwareCapabilityKeys.PRINT_FRONTDESK_RECEIPT,
                HardwareCapabilityKeys.PRINT_HOT_KITCHEN
            ),
            catalog.canonicalKeys("PRINTER_TOPOLOGY_FOR_REAL_OR_PAD_DIRECT")
        );
    }

    @Test
    void profileForbiddenPhysicalBindingFieldsAreNotCapabilities() {
        for (String prohibited : Set.of("printer_endpoint", "ip_address", "host", "device_token", "secret")) {
            assertFalse(catalog.supports(prohibited), prohibited + " must not become a profile capability");
        }
    }

    @Test
    void unknownCapabilityFailsClosedAtCatalogBoundary() {
        assertFalse(catalog.supports("CASH_DRAWER"));
        assertTrue(catalog.canonicalKeys("CASH_DRAWER").isEmpty());
    }
}
