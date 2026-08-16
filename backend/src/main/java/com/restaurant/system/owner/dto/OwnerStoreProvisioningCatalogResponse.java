package com.restaurant.system.owner.dto;

public class OwnerStoreProvisioningCatalogResponse {

    public Boolean enabled;
    public String profile_code;
    public String profile_version;
    public String master_menu_key;
    public String master_menu_version;
    public String master_menu_fingerprint_sha256;

    public static OwnerStoreProvisioningCatalogResponse initial(Boolean enabled) {
        OwnerStoreProvisioningCatalogResponse response = new OwnerStoreProvisioningCatalogResponse();
        response.enabled = enabled;
        response.profile_code = "ST_DENIS_CANONICAL_PROFILE";
        response.profile_version = "v2";
        response.master_menu_key = "LANZHOU_CHAIN_MASTER_MENU";
        response.master_menu_version = "v1";
        response.master_menu_fingerprint_sha256 = "e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7";
        return response;
    }
}
