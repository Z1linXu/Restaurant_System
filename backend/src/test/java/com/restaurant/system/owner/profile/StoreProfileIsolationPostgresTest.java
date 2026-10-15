package com.restaurant.system.owner.profile;

import static org.assertj.core.api.Assertions.*;
import com.restaurant.system.support.CommercialPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named = "COMMERCIAL_TEST_POSTGRES_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/commercial_[a-zA-Z0-9_]+")
class StoreProfileIsolationPostgresTest {
    @Test void v37UpgradeInfersOnlyUnambiguousExactProvenanceAndPreservesSnapshots() throws Exception {
        try (var db = new CommercialPostgres()) {
            Flyway.configure().dataSource(db.dataSource).target("37").load().migrate();
            var jdbc = new JdbcTemplate(db.dataSource);
            long a = jdbc.queryForObject("insert into organizations(name,code) values ('Synthetic A','synthetic_a') returning id", Long.class);
            long b = jdbc.queryForObject("insert into organizations(name,code) values ('Synthetic B','synthetic_b') returning id", Long.class);
            // Legacy seeds have no Organization provenance merely because V19 has a master per Org.
            jdbc.update("""
                insert into stores(name,code,organization_id,provisioned_profile_code,provisioned_profile_version,
                    provisioned_profile_fingerprint_sha256)
                select 'Synthetic A', 'synthetic_a', ?, p.profile_code, v.profile_version, v.fingerprint_sha256
                from store_profiles p join store_profile_versions v on v.profile_id=p.id
                where p.profile_code='ST_DENIS_CANONICAL_PROFILE' and v.profile_version='v2'
                """, a);
            var before = jdbc.queryForList("select id,content_json,fingerprint_sha256 from store_profile_versions order by id");
            var flyway = Flyway.configure().dataSource(db.dataSource).load();
            flyway.migrate(); flyway.validate();
            assertThat(jdbc.queryForObject("select owner_organization_id from store_profiles where profile_code='ST_DENIS_CANONICAL_PROFILE'", Long.class)).isEqualTo(a);
            assertThat(jdbc.queryForObject("select count(*) from store_profiles where visibility <> 'PRIVATE'", Integer.class)).isZero();
            assertThat(jdbc.queryForList("select id,content_json,fingerprint_sha256 from store_profile_versions order by id")).isEqualTo(before);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThatThrownBy(() -> jdbc.update("update store_profiles set visibility='PUBLIC_PRESET' where owner_organization_id=?", a))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("update store_profiles set owner_organization_id=?", b + 10000))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }

    @Test void freshDatabaseAndAmbiguousLegacyReferencesStayPrivateUnowned() throws Exception {
        try (var db = new CommercialPostgres()) {
            Flyway.configure().dataSource(db.dataSource).target("37").load().migrate();
            var jdbc = new JdbcTemplate(db.dataSource);
            for (String code : new String[]{"ambiguous_a", "ambiguous_b"}) {
                long org = jdbc.queryForObject("insert into organizations(name,code) values (?,?) returning id", Long.class, code, code);
                jdbc.update("""
                    insert into stores(name,code,organization_id,provisioned_profile_code,provisioned_profile_version,
                        provisioned_profile_fingerprint_sha256)
                    select ?, ?, ?, p.profile_code, v.profile_version, v.fingerprint_sha256
                    from store_profiles p join store_profile_versions v on v.profile_id=p.id
                    where p.profile_code='ST_DENIS_CANONICAL_PROFILE' and v.profile_version='v2'
                    """, code, code, org);
            }
            Flyway.configure().dataSource(db.dataSource).load().migrate();
            assertThat(jdbc.queryForObject("select count(*) from store_profiles where owner_organization_id is not null", Integer.class)).isZero();
        }
        try (var db = new CommercialPostgres()) {
            Flyway.configure().dataSource(db.dataSource).load().migrate();
            var jdbc = new JdbcTemplate(db.dataSource);
            assertThat(jdbc.queryForObject("select count(*) from store_profiles where owner_organization_id is not null or visibility <> 'PRIVATE'", Integer.class)).isZero();
        }
    }
}
