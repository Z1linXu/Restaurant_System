package com.restaurant.system.owner.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.profile.dto.StoreProfileArtifactResponse;
import com.restaurant.system.owner.profile.dto.StoreProfileSummaryResponse;
import com.restaurant.system.owner.profile.dto.StoreProfileVersionSummaryResponse;
import com.restaurant.system.owner.profile.dto.StoreProfileVersionResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StoreProfileCatalogService {

    private final StoreProfileRepository profileRepository;
    private final StoreProfileVersionRepository versionRepository;
    private final StoreProfileArtifactRepository artifactRepository;
    private final StoreProfileContractValidator validator;

    public StoreProfileCatalogService(
        StoreProfileRepository profileRepository,
        StoreProfileVersionRepository versionRepository,
        StoreProfileArtifactRepository artifactRepository,
        StoreProfileContractValidator validator
    ) {
        this.profileRepository = profileRepository;
        this.versionRepository = versionRepository;
        this.artifactRepository = artifactRepository;
        this.validator = validator;
    }

    public List<StoreProfileSummaryResponse> listProfiles() {
        return profileRepository.findAllByOrderByProfileCodeAsc().stream()
            .map(profile -> new StoreProfileSummaryResponse(
                profile.profile_code,
                profile.display_name,
                profile.description,
                profile.status,
                profile.provenance,
                versionRepository.findAllByProfileIdOrderByProfileVersionAsc(profile.id).stream()
                    .map(version -> new StoreProfileVersionSummaryResponse(
                        version.profile_version,
                        version.status,
                        version.schema_version,
                        version.fingerprint_sha256
                    ))
                    .toList()
            ))
            .toList();
    }

    public StoreProfileVersionResponse getVersion(String profileCode, String profileVersion) {
        StoreProfileEntity profile = profileRepository.findByProfileCode(profileCode)
            .orElseThrow(() -> new BusinessException("Store Profile not found"));
        StoreProfileVersionEntity version = versionRepository.findByProfileIdAndProfileVersion(profile.id, profileVersion)
            .orElseThrow(() -> new BusinessException("Store Profile version not found"));
        List<StoreProfileArtifactEntity> artifacts = artifactRepository
            .findAllByProfileVersionIdOrderByArtifactTypeAscArtifactCodeAsc(version.id);
        List<StoreProfileArtifactInput> artifactInputs = artifacts.stream()
            .map(artifact -> new StoreProfileArtifactInput(
                artifact.artifact_type,
                artifact.artifact_code,
                artifact.artifact_version,
                artifact.content_json,
                artifact.fingerprint_sha256
            ))
            .toList();
        StoreProfileValidationResult validation = validator.validate(
            profile.profile_code,
            version.profile_version,
            version.schema_version,
            version.content_json,
            version.fingerprint_sha256,
            artifactInputs
        );
        JsonNode content = StoreProfileCanonicalJson.parse(version.content_json);
        return new StoreProfileVersionResponse(
            profile.profile_code,
            profile.display_name,
            profile.description,
            profile.status,
            profile.provenance,
            version.profile_version,
            version.status,
            version.schema_version,
            version.fingerprint_sha256,
            validation.valid(),
            validation.issues(),
            content,
            artifacts.stream()
                .map(artifact -> new StoreProfileArtifactResponse(
                    artifact.artifact_type,
                    artifact.artifact_code,
                    artifact.artifact_version,
                    artifact.fingerprint_sha256,
                    StoreProfileCanonicalJson.parse(artifact.content_json)
                ))
                .toList()
        );
    }
}
