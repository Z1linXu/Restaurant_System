package com.restaurant.system.modules.dto;

import java.util.List;

public class StoreModuleConfigurationResponse {
    public Long store_id;
    public String catalog_version;
    public String dependency_graph_version;
    public Boolean valid;
    public String validation_status;
    public String environment_capability_source;
    public String hardware_capability_source;
    public String legacy_compatibility_status;
    public String legacy_precedence;
    public List<String> environment_capabilities;
    public List<String> hardware_capabilities;
    public List<StoreModuleResponse> modules;
    public List<StoreModuleValidationIssueResponse> validation_issues;
}
