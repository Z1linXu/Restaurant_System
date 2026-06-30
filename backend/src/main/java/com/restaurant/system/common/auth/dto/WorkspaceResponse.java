package com.restaurant.system.common.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class WorkspaceResponse {
    @JsonProperty("default_store_id")
    public Long defaultStoreId;

    @JsonProperty("organizations")
    public List<OrganizationWorkspace> organizations;

    @JsonProperty("stores")
    public List<StoreWorkspace> stores;

    public static class OrganizationWorkspace {
        public Long id;
        public String name;
        public String code;
        public String status;

        @JsonProperty("role_code")
        public String roleCode;
    }

    public static class StoreWorkspace {
        public Long id;
        public String name;
        public String code;
        public String status;

        @JsonProperty("organization_id")
        public Long organizationId;

        @JsonProperty("role_code")
        public String roleCode;
    }
}
