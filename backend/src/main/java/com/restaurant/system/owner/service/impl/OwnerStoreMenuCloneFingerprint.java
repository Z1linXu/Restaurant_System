package com.restaurant.system.owner.service.impl;

import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneReservationCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class OwnerStoreMenuCloneFingerprint {

    public String fingerprint(OwnerStoreMenuCloneReservationCommand command) {
        String canonical = value("contract") + value(ChinatownMenuCloneProfile.CONTRACT_VERSION)
            + value("organization") + value(String.valueOf(command.organizationId()))
            + value("sourceStore") + value(String.valueOf(command.sourceStoreId()))
            + value("targetStore") + value(String.valueOf(command.targetStoreId()))
            + value("profile") + value(command.profileCode());
        return sha256(canonical);
    }

    private String value(String value) {
        String normalized = value == null ? "" : value;
        return normalized.length() + ":" + normalized;
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                hex.append(String.format("%02x", valueByte));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
