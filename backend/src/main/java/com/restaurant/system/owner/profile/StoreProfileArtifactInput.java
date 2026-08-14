package com.restaurant.system.owner.profile;

public record StoreProfileArtifactInput(
    String artifactType,
    String artifactCode,
    String artifactVersion,
    String contentJson,
    String fingerprintSha256
) {
}
