package com.restaurant.system.owner.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.owner.profile.StoreProfileValidationIssue;
import java.util.List;

public record StoreProfileVersionResponse(
    @JsonProperty("profile_code") String profileCode,
    @JsonProperty("display_name") String displayName,
    String description,
    @JsonProperty("profile_status") String profileStatus,
    String provenance,
    @JsonProperty("profile_version") String profileVersion,
    @JsonProperty("version_status") String versionStatus,
    @JsonProperty("schema_version") String schemaVersion,
    @JsonProperty("fingerprint_sha256") String fingerprintSha256,
    boolean valid,
    @JsonProperty("validation_issues") List<StoreProfileValidationIssue> validationIssues,
    JsonNode content,
    List<StoreProfileArtifactResponse> artifacts
) {

    public StoreProfileVersionResponse {
        validationIssues = validationIssues == null ? List.of() : List.copyOf(validationIssues);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
