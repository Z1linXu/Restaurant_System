package com.restaurant.system.owner.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record StoreProfileArtifactResponse(
    @JsonProperty("artifact_type") String artifactType,
    @JsonProperty("artifact_code") String artifactCode,
    @JsonProperty("artifact_version") String artifactVersion,
    @JsonProperty("fingerprint_sha256") String fingerprintSha256,
    JsonNode content
) {
}
