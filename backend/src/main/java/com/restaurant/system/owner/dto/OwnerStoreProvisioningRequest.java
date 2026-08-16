package com.restaurant.system.owner.dto;

import jakarta.validation.constraints.NotBlank;

public class OwnerStoreProvisioningRequest {

    @NotBlank
    public String store_name;

    @NotBlank
    public String store_code;

    public String profile_code;

    public String profile_version;

    public String profile_fingerprint_sha256;

    public String master_menu_key;

    public String master_menu_version;

    public String master_menu_fingerprint_sha256;
}
