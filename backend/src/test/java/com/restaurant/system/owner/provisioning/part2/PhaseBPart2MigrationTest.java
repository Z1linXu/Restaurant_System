package com.restaurant.system.owner.provisioning.part2;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseBPart2MigrationTest {

    @Test
    void migrationIsAdditiveAndContainsNoRuntimeSecrets() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V23__add_phase_b_part2_readiness_and_activation.sql");
        String sql = Files.readString(migration).toLowerCase();

        assertTrue(sql.contains("create table store_readiness_evidence"));
        assertTrue(sql.contains("create table store_provisioning_part2_requests"));
        assertTrue(sql.contains("create table store_logical_printer_roles"));
        assertTrue(sql.contains("create table store_device_readiness"));
        assertTrue(sql.contains("create table store_activation_requests"));
        assertFalse(sql.contains("drop table"));
        assertFalse(sql.contains("password_hash"));
        assertFalse(sql.contains("device_token"));
        assertFalse(sql.contains("ip_address"));
        assertFalse(sql.contains("insert into public.stores"));

        Path hardeningMigration = Path.of("src/main/resources/db/migration/V24__harden_phase_b_part2_evidence_and_device_heartbeat.sql");
        String hardeningSql = Files.readString(hardeningMigration).toLowerCase();
        assertTrue(hardeningSql.contains("create table store_readiness_evidence_history"));
        assertTrue(hardeningSql.contains("last_heartbeat_at"));
        assertFalse(hardeningSql.contains("drop table"));
        assertFalse(hardeningSql.contains("password_hash"));
        assertFalse(hardeningSql.contains("device_token"));
        assertFalse(hardeningSql.contains("ip_address"));
    }
}
