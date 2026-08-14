package com.restaurant.system.owner.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StoreProfileVersionSummaryResponse(
    @JsonProperty("profile_version") String profileVersion,
    String status,
    @JsonProperty("schema_version") String schemaVersion,
    @JsonProperty("fingerprint_sha256") String fingerprintSha256
) {
}
