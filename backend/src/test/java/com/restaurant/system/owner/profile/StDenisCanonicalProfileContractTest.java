package com.restaurant.system.owner.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class StDenisCanonicalProfileContractTest {

    private static final String PROFILE_CODE = "ST_DENIS_CANONICAL_PROFILE";
    private static final String PROFILE_VERSION = "v1";
    private static final String SCHEMA_VERSION = StoreProfileContractValidator.SCHEMA_VERSION;

    private static SqlProfileSeed seed;

    @BeforeAll
    static void loadSeed() throws IOException {
        String sql = new ClassPathResource(
            "db/migration/V15__seed_st_denis_canonical_profile.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
        seed = parseSeed(sql);
    }

    @Test
    void stDenisCanonicalProfileValidatesAgainstA4Contract() {
        StoreProfileContractValidator validator = new StoreProfileContractValidator();

        StoreProfileValidationResult result = validator.validate(
            PROFILE_CODE,
            PROFILE_VERSION,
            SCHEMA_VERSION,
            seed.profileContentJson(),
            seed.profileFingerprint(),
            seed.artifacts()
        );

        assertThat(result.valid()).as(result.issues().toString()).isTrue();
        assertThat(result.computedFingerprint()).isEqualTo(seed.profileFingerprint());
    }

    @Test
    void stDenisCanonicalProfileMaterializationDryRunPreservesCompleteSafeGraph() {
        StoreProfileMaterializationDryRunValidator validator = new StoreProfileMaterializationDryRunValidator();

        StoreProfileMaterializationDryRunValidator.StoreProfileMaterializationDryRunResult result =
            validator.validate(seed.profileContentJson(), seed.artifacts());

        assertThat(result.valid()).as(result.issues().toString()).isTrue();
        assertThat(result.counts().categoryCount()).isEqualTo(6);
        assertThat(result.counts().itemCount()).isEqualTo(39);
        assertThat(result.counts().optionCount()).isEqualTo(380);
        assertThat(result.counts().parentOptionRelationshipCount()).isEqualTo(11);
        assertThat(result.counts().tableCount()).isEqualTo(13);
        assertThat(result.counts().stationCount()).isEqualTo(5);
        assertThat(result.counts().logicalPrinterCount()).isEqualTo(4);
        assertThat(result.counts().printerAssignmentCount()).isEqualTo(3);
        assertThat(result.counts().comboComponentCount()).isEqualTo(5);
        assertThat(result.counts().staffTemplateCount()).isEqualTo(4);
    }

    @Test
    void profileFingerprintIsDeterministicAndChangesWithBusinessContent() {
        StoreProfileContractValidator validator = new StoreProfileContractValidator();

        String first = validator.computeAggregateFingerprint(
            PROFILE_CODE,
            PROFILE_VERSION,
            SCHEMA_VERSION,
            seed.profileContentJson(),
            seed.artifacts()
        );
        String second = validator.computeAggregateFingerprint(
            PROFILE_CODE,
            PROFILE_VERSION,
            SCHEMA_VERSION,
            seed.profileContentJson(),
            seed.artifacts()
        );
        String changed = validator.computeAggregateFingerprint(
            PROFILE_CODE,
            PROFILE_VERSION,
            SCHEMA_VERSION,
            seed.profileContentJson().replace("\"items\": 39", "\"items\": 40"),
            seed.artifacts()
        );

        assertThat(first).isEqualTo(seed.profileFingerprint());
        assertThat(second).isEqualTo(seed.profileFingerprint());
        assertThat(changed).isNotEqualTo(seed.profileFingerprint());
    }

    @Test
    void artifactFingerprintsMatchCanonicalContent() {
        assertThat(seed.artifacts()).hasSize(12);
        assertThat(seed.artifacts())
            .allSatisfy(artifact -> assertThat(artifact.fingerprintSha256())
                .isEqualTo(StoreProfileCanonicalJson.sha256Canonical(artifact.contentJson())));
        assertThat(seed.artifacts())
            .extracting(StoreProfileArtifactInput::artifactCode)
            .containsExactlyInAnyOrder(
                "MODULE_DEFAULTS",
                "MENU_TEMPLATE",
                "PRICING_POLICY",
                "COMBO_CONFIGURATION",
                "TABLE_TEMPLATE",
                "STATION_TEMPLATE",
                "PRINTING_TOPOLOGY",
                "ROLE_ACCESS_DEFAULTS",
                "HARDWARE_REQUIREMENTS",
                "DEVICE_CAPABILITY_REQUIREMENTS",
                "OPERATIONAL_SETTINGS",
                "FEATURE_DEFAULTS"
            );
    }

    @Test
    void profileUsesProfileLocalRefsAndRejectsOrphanMenuRelationships() {
        StoreProfileArtifactInput menu = seed.artifact("MENU_TEMPLATE");
        StoreProfileArtifactInput invalidMenu = new StoreProfileArtifactInput(
            menu.artifactType(),
            menu.artifactCode(),
            menu.artifactVersion(),
            menu.contentJson().replaceFirst(
                "\"station_ref\": \"STA-001\"",
                "\"station_ref\" : \"STA-MISSING\""
            ),
            menu.fingerprintSha256()
        );
        List<StoreProfileArtifactInput> invalidArtifacts = seed.artifacts().stream()
            .map(artifact -> "MENU_TEMPLATE".equals(artifact.artifactCode()) ? invalidMenu : artifact)
            .toList();

        StoreProfileMaterializationDryRunValidator.StoreProfileMaterializationDryRunResult result =
            new StoreProfileMaterializationDryRunValidator().validate(seed.profileContentJson(), invalidArtifacts);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.startsWith("MENU_ITEM_STATION_REF_MISSING"));
    }

    @Test
    void profileSeedContainsNoSourceDatabaseIdsOrProhibitedRuntimeFields() {
        assertNoForbiddenRuntimeField(StoreProfileCanonicalJson.parse(seed.profileContentJson()), "$profile");
        for (StoreProfileArtifactInput artifact : seed.artifacts()) {
            assertNoForbiddenRuntimeField(StoreProfileCanonicalJson.parse(artifact.contentJson()), artifact.artifactCode());
        }
    }

    @Test
    void v15PublishesOnlyAfterArtifactInsertSoImmutableTriggersRemainRespected() {
        String sql = seed.sql().toLowerCase();

        assertThat(sql).contains("v15__seed_st_denis_canonical_profile");
        assertThat(sql).contains("'draft'");
        assertThat(sql).contains("insert into public.store_profile_artifacts");
        assertThat(sql).contains("set status = 'ready'");
        assertThat(sql.indexOf("insert into public.store_profile_artifacts"))
            .isLessThan(sql.indexOf("set status = 'ready'"));
        assertThat(sql)
            .doesNotContain("delete from")
            .doesNotContain("truncate")
            .doesNotContain("drop table")
            .doesNotContain("update public.orders")
            .doesNotContain("update public.order_items")
            .doesNotContain("update public.print_jobs")
            .doesNotContain("update public.users");
    }

    private static void assertNoForbiddenRuntimeField(JsonNode node, String path) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                assertNoForbiddenRuntimeField(node.get(index), path + "[" + index + "]");
            }
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String normalized = entry.getKey().toLowerCase();
            assertThat(normalized)
                .as(path + "." + entry.getKey())
                .isNotEqualTo("id")
                .doesNotEndWith("_id")
                .doesNotEndWith("_ids")
                .doesNotContain("password")
                .doesNotContain("token")
                .doesNotContain("credential")
                .doesNotContain("secret")
                .doesNotContain("endpoint")
                .isNotEqualTo("host")
                .isNotEqualTo("port")
                .isNotEqualTo("ip")
                .isNotEqualTo("ip_address");
            assertNoForbiddenRuntimeField(entry.getValue(), path + "." + entry.getKey());
        });
    }

    private static SqlProfileSeed parseSeed(String sql) {
        Pattern profilePattern = Pattern.compile(
            "\\$profile_content\\$\\n(?<json>.*?)\\n\\s*\\$profile_content\\$,\\n\\s*'(?<fingerprint>[0-9a-f]{64})'",
            Pattern.DOTALL
        );
        Matcher profileMatcher = profilePattern.matcher(sql);
        assertThat(profileMatcher.find()).isTrue();

        Pattern artifactPattern = Pattern.compile(
            "'(?<type>[A-Z_]+)',\\n\\s*'(?<code>[A-Z_]+)',\\n\\s*'(?<version>[^']+)',\\n"
                + "\\s*\\$(?<tag>artifact_[a-z_]+)\\$\\n(?<json>.*?)\\n\\s*\\$\\k<tag>\\$,\\n"
                + "\\s*'(?<fingerprint>[0-9a-f]{64})'",
            Pattern.DOTALL
        );
        Matcher artifactMatcher = artifactPattern.matcher(sql);
        List<StoreProfileArtifactInput> artifacts = new ArrayList<>();
        while (artifactMatcher.find()) {
            artifacts.add(new StoreProfileArtifactInput(
                artifactMatcher.group("type"),
                artifactMatcher.group("code"),
                artifactMatcher.group("version"),
                artifactMatcher.group("json"),
                artifactMatcher.group("fingerprint")
            ));
        }
        return new SqlProfileSeed(
            sql,
            profileMatcher.group("json"),
            profileMatcher.group("fingerprint"),
            List.copyOf(artifacts)
        );
    }

    private record SqlProfileSeed(
        String sql,
        String profileContentJson,
        String profileFingerprint,
        List<StoreProfileArtifactInput> artifacts
    ) {

        StoreProfileArtifactInput artifact(String artifactCode) {
            return artifacts.stream()
                .filter(artifact -> artifactCode.equals(artifact.artifactCode()))
                .findFirst()
                .orElseThrow();
        }
    }
}
