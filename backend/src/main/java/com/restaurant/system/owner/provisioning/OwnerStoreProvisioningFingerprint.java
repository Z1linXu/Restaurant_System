package com.restaurant.system.owner.provisioning;

import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class OwnerStoreProvisioningFingerprint {

    public String fingerprint(OwnerStoreProvisioningCommand command) {
        String canonical = Stream.of(
                "purpose=" + safe(command.purpose()),
                "organization_id=" + safe(command.organizationId()),
                "store_name=" + safe(command.storeName()),
                "store_code=" + safe(command.storeCode()),
                "profile_code=" + safe(command.profileCode()),
                "profile_version=" + safe(command.profileVersion()),
                "profile_fingerprint_sha256=" + safe(command.profileFingerprintSha256()),
                "master_menu_key=" + safe(command.masterMenuKey()),
                "master_menu_version=" + safe(command.masterMenuVersion()),
                "master_menu_fingerprint_sha256=" + safe(command.masterMenuFingerprintSha256())
            )
            .collect(Collectors.joining("\n"));
        return StoreProfileCanonicalJson.sha256(canonical);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
