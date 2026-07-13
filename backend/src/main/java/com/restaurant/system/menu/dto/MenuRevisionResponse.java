package com.restaurant.system.menu.dto;

import java.time.LocalDateTime;

public class MenuRevisionResponse {

    public Long store_id;
    public Long organization_id;
    public Long menu_revision;
    public LocalDateTime menu_updated_at;
    public String catalog_version;
    public String tax_policy_version;
    public String etag;

    public MenuRevisionResponse(
        Long storeId,
        Long organizationId,
        Long menuRevision,
        LocalDateTime menuUpdatedAt,
        String catalogVersion,
        String taxPolicyVersion,
        String etag
    ) {
        this.store_id = storeId;
        this.organization_id = organizationId;
        this.menu_revision = menuRevision;
        this.menu_updated_at = menuUpdatedAt;
        this.catalog_version = catalogVersion;
        this.tax_policy_version = taxPolicyVersion;
        this.etag = etag;
    }
}
