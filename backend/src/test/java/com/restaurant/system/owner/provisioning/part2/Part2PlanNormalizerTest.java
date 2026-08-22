package com.restaurant.system.owner.provisioning.part2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Part2PlanNormalizerTest {

    private final Part2PlanNormalizer normalizer = new Part2PlanNormalizer(new ObjectMapper());

    @Test
    void normalizesStoreLocalCodesAndRedactsRuntimeCredentials() {
        StorePart2ProvisioningRequest request = new StorePart2ProvisioningRequest();
        StorePart2ProvisioningRequest.TableSpec table = new StorePart2ProvisioningRequest.TableSpec();
        table.table_code = " t 01 ";
        table.table_name = "Synthetic Table";
        request.tables.add(table);
        StorePart2ProvisioningRequest.StaffSpec staff = new StorePart2ProvisioningRequest.StaffSpec();
        staff.role_code = "frontdesk";
        staff.login_identifier = "synthetic-frontdesk";
        staff.temporary_password = "synthetic-secret";
        request.staff.add(staff);

        Part2ProvisioningPlan plan = normalizer.normalize(request);

        assertTrue(plan.tables().stream().anyMatch(value -> "T_01".equals(value.code())));
        assertFalse(plan.sanitizedJson().contains("synthetic-secret"));
        assertTrue(plan.sanitizedJson().contains("BCrypt_RUNTIME_ONLY"));
        assertTrue(plan.fingerprint().matches("[0-9a-f]{64}"));
    }

    @Test
    void rejectsRealPrinterModes() {
        StorePart2ProvisioningRequest request = new StorePart2ProvisioningRequest();
        StorePart2ProvisioningRequest.PrinterRoleSpec role = new StorePart2ProvisioningRequest.PrinterRoleSpec();
        role.role_code = "GRAB";
        role.module_code = "GRAB";
        role.mode = "REAL";
        request.printer_roles.add(role);

        assertThrows(BusinessException.class, () -> normalizer.normalize(request));
    }
}
