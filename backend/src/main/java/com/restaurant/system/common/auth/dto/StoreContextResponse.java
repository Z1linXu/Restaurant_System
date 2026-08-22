package com.restaurant.system.common.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.system.modules.dto.StoreModuleConfigurationResponse;

public class StoreContextResponse {
    public Long id;
    public String name;
    public String code;
    public String status;

    @JsonProperty("store_kind")
    public String storeKind;

    @JsonProperty("lifecycle_status")
    public String lifecycleStatus;

    @JsonProperty("operational_state")
    public String operationalState;

    @JsonProperty("is_live")
    public Boolean live;

    @JsonProperty("provisioning_source")
    public String provisioningSource;

    @JsonProperty("provisioned_profile_code")
    public String provisionedProfileCode;

    @JsonProperty("provisioned_profile_version")
    public String provisionedProfileVersion;

    @JsonProperty("provisioned_master_menu_key")
    public String provisionedMasterMenuKey;

    @JsonProperty("provisioned_master_menu_version")
    public String provisionedMasterMenuVersion;

    @JsonProperty("organization_id")
    public Long organizationId;

    @JsonProperty("organization_name")
    public String organizationName;

    @JsonProperty("organization_code")
    public String organizationCode;

    @JsonProperty("role_code")
    public String roleCode;

    @JsonProperty("module_configuration")
    public StoreModuleConfigurationResponse moduleConfiguration;
}
