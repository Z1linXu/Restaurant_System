package com.restaurant.system.printing.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "PRINTING_RULE_POSTGRES_URL", matches = "jdbc:postgresql:.*")
@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.flyway.baseline-on-migrate=true",
    "spring.flyway.validate-on-migrate=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PrintingDisplayRuleLifecyclePostgresIntegrationTest.JpaSliceConfiguration.class)
class PrintingDisplayRuleLifecyclePostgresIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironment("PRINTING_RULE_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironment("PRINTING_RULE_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("PRINTING_RULE_POSTGRES_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void v22AllowsHistoricalFingerprintReuseAndKeepsDraftAndImmutabilityGuards() {
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '22' and success",
            Integer.class
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from pg_constraint where conname = 'uq_printing_display_rule_revisions_fingerprint'",
            Integer.class
        )).isZero();
        assertThat(indexDefinition("idx_printing_display_rule_revisions_set_fingerprint"))
            .contains("rule_set_id", "fingerprint_sha256")
            .doesNotContain("UNIQUE");
        assertThat(indexDefinition("uq_printing_display_rule_revisions_single_draft"))
            .contains("UNIQUE", "rule_set_id", "WHERE", "status", "DRAFT");

        String suffix = UUID.randomUUID().toString().replace("-", "");
        Long organizationId = inTransaction(() -> jdbcTemplate.queryForObject(
            """
            insert into organizations (code, name, status, created_at, updated_at)
            values (?, ?, 'active', current_timestamp, current_timestamp)
            returning id
            """,
            Long.class,
            "RULE_PG_ORG_" + suffix,
            "Rule PG Org " + suffix
        ));
        Long storeId = inTransaction(() -> jdbcTemplate.queryForObject(
            """
            insert into stores (code, name, status, organization_id, printing_enabled, printing_mode, created_at, updated_at)
            values (?, ?, 'active', ?, false, 'MOCK', current_timestamp, current_timestamp)
            returning id
            """,
            Long.class,
            "RULE_PG_" + suffix,
            "Rule PG " + suffix,
            organizationId
        ));
        Long ruleSetId = inTransaction(() -> jdbcTemplate.queryForObject(
            """
            insert into printing_display_rule_sets (store_id, status, created_at, updated_at)
            values (?, 'ACTIVE', current_timestamp, current_timestamp)
            returning id
            """,
            Long.class,
            storeId
        ));
        String fingerprint = "a".repeat(64);
        Long firstPublishedId = insertRevision(ruleSetId, 1, "PUBLISHED", fingerprint, true);
        Long rollbackPublishedId = insertRevision(ruleSetId, 2, "PUBLISHED", fingerprint, true);
        Long draftId = insertRevision(ruleSetId, 3, "DRAFT", "b".repeat(64), false);
        inTransaction(() -> {
            jdbcTemplate.update(
                "update printing_display_rule_sets set active_revision_id = ?, updated_at = current_timestamp where id = ?",
                rollbackPublishedId,
                ruleSetId
            );
            return null;
        });

        assertThat(firstPublishedId).isNotEqualTo(rollbackPublishedId);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from printing_display_rule_revisions where rule_set_id = ? and fingerprint_sha256 = ?",
            Integer.class,
            ruleSetId,
            fingerprint
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select active_revision_id from printing_display_rule_sets where id = ?",
            Long.class,
            ruleSetId
        )).isEqualTo(rollbackPublishedId);

        assertThatThrownBy(() -> insertRevision(ruleSetId, 4, "DRAFT", "c".repeat(64), false))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uq_printing_display_rule_revisions_single_draft");
        assertThatThrownBy(() -> inTransaction(() -> {
            jdbcTemplate.update(
                "update printing_display_rule_revisions set summary = 'rewritten' where id = ?",
                firstPublishedId
            );
            return null;
        }))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("published printing display rule revisions are immutable");

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from printing_display_rule_revisions where id = ? and status = 'DRAFT'",
            Integer.class,
            draftId
        )).isOne();
    }

    private Long insertRevision(Long ruleSetId, int number, String status, String fingerprint, boolean published) {
        return inTransaction(() -> jdbcTemplate.queryForObject(
            """
            insert into printing_display_rule_revisions (
                rule_set_id, revision_number, status, schema_version, content_json,
                fingerprint_sha256, source_reference, summary, created_at, updated_at, published_at
            ) values (?, ?, ?, 'PRINTING_DISPLAY_RULES_V1', '{"schema_version":"PRINTING_DISPLAY_RULES_V1"}',
                ?, 'TEST', 'PostgreSQL lifecycle test', current_timestamp, current_timestamp,
                case when ? then current_timestamp else null end)
            returning id
            """,
            Long.class,
            ruleSetId,
            number,
            status,
            fingerprint,
            published
        ));
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
            "select indexdef from pg_indexes where schemaname = 'public' and indexname = ?",
            String.class,
            indexName
        );
    }

    private <T> T inTransaction(java.util.function.Supplier<T> supplier) {
        return new TransactionTemplate(transactionManager).execute(status -> supplier.get());
    }

    private static String requiredEnvironment(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required");
        }
        return value;
    }
}
