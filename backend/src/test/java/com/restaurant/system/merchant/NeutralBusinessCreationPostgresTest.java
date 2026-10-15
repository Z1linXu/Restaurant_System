package com.restaurant.system.merchant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.audit.service.impl.AuditLogServiceImpl;
import com.restaurant.system.auth.filter.AuthTokenFilter;
import com.restaurant.system.auth.service.impl.TokenServiceImpl;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.menu.addon.StoreAddonService;
import com.restaurant.system.menu.service.impl.MenuRevisionServiceImpl;
import com.restaurant.system.owner.controller.OwnerBusinessStoreCreateController;
import com.restaurant.system.owner.master.ChainMasterMenuCatalogService;
import com.restaurant.system.owner.profile.StoreProfileContractValidator;
import com.restaurant.system.owner.provisioning.*;
import com.restaurant.system.owner.provisioning.part2.*;
import com.restaurant.system.owner.service.impl.OwnerBusinessStoreCreateServiceImpl;
import com.restaurant.system.support.CommercialPostgres;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.*;
import org.springframework.web.filter.RequestContextFilter;

/** Full real PG/JPA provisioning and HTTP boundary. Only unrelated optional work is mocked. */
@EnabledIfEnvironmentVariable(named = "COMMERCIAL_TEST_POSTGRES_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/commercial_[a-zA-Z0-9_]+")
class NeutralBusinessCreationPostgresTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    @Import({MerchantConfigurationService.class, OwnerStoreProvisioningServiceImpl.class,
        OwnerBusinessStoreCreateServiceImpl.class, OwnerStoreProvisioningMaterializer.class,
        OwnerStoreProvisioningRequestCoordinatorImpl.class, OperationalStoreBaselineProvisioner.class,
        OwnerStoreProvisioningFingerprint.class, OwnerOrganizationAuthorizationService.class,
        StoreProfileContractValidator.class, ChainMasterMenuCatalogService.class, MenuRevisionServiceImpl.class,
        AuthorizationService.class, RoleCapabilityRegistry.class, StoreAccessService.class,
        AuditLogServiceImpl.class, OwnerStoreProvisioningCatalogService.class})
    static class Backend {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source);
            factory.setPackagesToScan("com.restaurant.system"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "validate")); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean @Primary EntityManager entityManager(EntityManagerFactory emf) { return SharedEntityManagerCreator.createSharedEntityManager(emf); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean TokenServiceImpl tokens(ObjectMapper json) { return new TokenServiceImpl(json, "synthetic-test-signing-key-never-a-runtime-secret", 900); }
        @Bean BearerUserResolver identities(TokenServiceImpl tokens, com.restaurant.system.user.repository.UserRepository users,
            com.restaurant.system.user.repository.RoleRepository roles) { return new BearerUserResolver(tokens, users, roles); }
        @Bean RequestUserContextService requests() {
            var context = mock(RequestUserContextService.class);
            when(context.getRequiredUser()).thenAnswer(i -> {
                var attributes = RequestContextHolder.getRequestAttributes();
                Object actor = attributes instanceof ServletRequestAttributes a ? a.getRequest()
                    .getAttribute(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE) : null;
                if (actor instanceof AuthenticatedUser user) return user;
                throw new UnauthorizedException("Bearer context required");
            }); return context;
        }
        @Bean FeatureFlagService flags() { return mock(FeatureFlagService.class); }
        @Bean PhaseBProvisioningRuntimeGate gate() { return mock(PhaseBProvisioningRuntimeGate.class); }
        @Bean StoreAddonService addons() { return mock(StoreAddonService.class); }
        @Bean PhaseBPart1ProvisioningValidator legacyValidator() { return mock(PhaseBPart1ProvisioningValidator.class); }
        @Bean StoreReadinessService legacyReadiness() { return mock(StoreReadinessService.class); }
        @Bean StoreActivationRequestCoordinator legacyActivation() { return mock(StoreActivationRequestCoordinator.class); }
    }

    @Test void ownerEndpointCreatesEmptyNeutralAtomicallyAndReplaysWithoutLegacyWork() throws Exception {
        try (var db = new CommercialPostgres()) {
            Flyway.configure().dataSource(db.dataSource).load().migrate();
            var jdbc = new JdbcTemplate(db.dataSource);
            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(DataSource.class, () -> db.dataSource); context.register(Backend.class); context.refresh();
                long a = organization(jdbc, "synthetic_neutral_a"), b = organization(jdbc, "synthetic_neutral_b");
                long role = jdbc.queryForObject("insert into roles(code,name) values ('OWNER','Synthetic Owner') returning id", Long.class);
                long user = jdbc.queryForObject("insert into users(username,role_id,status,full_name) values ('synthetic_owner',?,'active','Synthetic Owner') returning id", Long.class, role);
                jdbc.update("insert into organization_memberships(organization_id,user_id,role_id,role_code,is_active) values (?,?,?,'OWNER',true)", a,user,role);
                var actor = new AuthenticatedUser(user, null, role, "synthetic_owner", "Synthetic Owner", "OWNER");
                String bearer = "Bearer " + context.getBean(TokenServiceImpl.class).createAccessToken(actor, a);
                var mvc = MockMvcBuilders.standaloneSetup(new OwnerBusinessStoreCreateController(
                        context.getBean(OwnerStoreProvisioningCatalogService.class), context.getBean(AuthorizationService.class),
                        context.getBean(OwnerOrganizationAuthorizationService.class), context.getBean(OwnerBusinessStoreCreateServiceImpl.class)),
                        new MerchantConfigurationController(context.getBean(MerchantConfigurationService.class)))
                    .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestContextFilter(),
                        new AuthTokenFilter(context.getBean(BearerUserResolver.class), new ObjectMapper())).build();
                String route = "/api/v1/owner/organizations/" + a + "/stores";
                String body = "{\"store_name\":\"Synthetic Neutral\",\"store_code\":\"SYNTHETIC_NEUTRAL\"}";
                var result = mvc.perform(post(route).header("Authorization", bearer).header("Idempotency-Key", "neutral-create")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.is_live").value(true)).andReturn();
                long store = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data").path("store_id").longValue();
                assertThat(context.getBean(MerchantConfigurationService.class).safeSummary(store).compatibility_mode()).isEqualTo("NEUTRAL");
                assertThat(jdbc.queryForObject("select provisioned_master_menu_key from stores where id=?", String.class, store)).isNull();
                for (String table : List.of("menu_categories","menu_items","stations","dining_tables","printer_configs",
                    "store_devices","store_logical_printer_roles","store_pricing_policies","store_combo_groups","store_menu_master_mappings")) {
                    assertThat(jdbc.queryForObject("select count(*) from " + table + " where store_id=?", Integer.class, store)).as(table).isZero();
                }
                assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from store_memberships where store_id=? and user_id=? and role_code='OWNER'", Integer.class, store,user)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from store_modules where store_id=? and module_key in ('KDS','UBER_EATS','ANALYTICS_ADVANCED') and enabled", Integer.class, store)).isZero();
                mvc.perform(post(route).header("Authorization", bearer).header("Idempotency-Key", "neutral-create")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.store_id").value(store)).andExpect(jsonPath("$.data.replayed").value(true));
                mvc.perform(post("/api/v1/owner/organizations/"+b+"/stores").header("Authorization", bearer).header("Idempotency-Key", "foreign")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
                mvc.perform(post(route).header("Authorization", bearer).header("Idempotency-Key", "private-profile")
                    .contentType(MediaType.APPLICATION_JSON).content(body.substring(0,body.length()-1)+",\"profile_code\":\"ST_DENIS_CANONICAL_PROFILE\"}"))
                    .andExpect(status().isForbidden());
                mvc.perform(get(route+"/create-catalog").header("Authorization", bearer)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.profile_code").value("NEUTRAL_RESTAURANT"))
                    .andExpect(jsonPath("$.data.master_menu_key").doesNotExist());
                verifyNoInteractions(context.getBean(StoreAddonService.class), context.getBean(StoreReadinessService.class),
                    context.getBean(StoreActivationRequestCoordinator.class));
                MerchantConfigurationPostgresTest.seedHistory(jdbc, store, a);
                var frozenBefore = MerchantConfigurationPostgresTest.history(jdbc);
                proveRevisionAndStaleScope(context, jdbc, mvc, bearer, store, a, b);
                assertThat(MerchantConfigurationPostgresTest.history(jdbc)).isEqualTo(frozenBefore);
                jdbc.execute("""
                    CREATE FUNCTION synthetic_reject_membership() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN IF EXISTS (SELECT 1 FROM stores WHERE id=NEW.store_id AND code='SYNTHETIC_FAIL')
                        THEN RAISE EXCEPTION 'synthetic late membership failure'; END IF; RETURN NEW; END $$
                    """);
                jdbc.execute("CREATE TRIGGER synthetic_failure BEFORE INSERT ON store_memberships FOR EACH ROW EXECUTE FUNCTION synthetic_reject_membership()");
                mvc.perform(post(route).header("Authorization", bearer).header("Idempotency-Key", "neutral-fail")
                    .contentType(MediaType.APPLICATION_JSON).content(body.replace("SYNTHETIC_NEUTRAL","SYNTHETIC_FAIL")))
                    .andExpect(status().isInternalServerError());
                assertThat(jdbc.queryForObject("select count(*) from stores where code='SYNTHETIC_FAIL'", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("select count(*) from store_merchant_configurations c join stores s on s.id=c.store_id where s.code='SYNTHETIC_FAIL'", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("select status from owner_store_provisioning_requests where organization_id=? and idempotency_key='neutral-fail'", String.class,a)).isEqualTo("FAILED");
            }
        }
    }

    private void proveRevisionAndStaleScope(AnnotationConfigApplicationContext context, JdbcTemplate jdbc, MockMvc mvc,
        String bearer, long store, long a, long b) throws Exception {
        String path = "/api/v1/stores/" + store + "/merchant-configuration";
        String body = "{\"schema_version\":1,\"expected_config_revision\":1,\"brand_name\":\"Changed Brand\",\"locale\":\"fr-CA\"}";
        var pool = Executors.newFixedThreadPool(2);
        try {
            var go = new CountDownLatch(1);
            Callable<Integer> operation = () -> { go.await(); return mvc.perform(put(path).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus(); };
            var first = pool.submit(operation); var second = pool.submit(operation); go.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS), second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("select config_revision from store_merchant_configurations where store_id=?", Long.class,store)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where store_id=? and action='MERCHANT_CONFIGURATION_UPDATED'", Integer.class,store)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select menu_revision from stores where id=?", Long.class,store)).isEqualTo(2);
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status -> {
            var em = context.getBean(EntityManager.class); Store stale = em.find(Store.class,store);
            assertThat(stale.organization_id).isEqualTo(a);
            try (var external = jdbc.getDataSource().getConnection();
                 var move = external.prepareStatement("update stores set organization_id=? where id=?")) {
                move.setLong(1,b); move.setLong(2,store); move.executeUpdate();
            } catch (java.sql.SQLException failure) { throw new RuntimeException(failure); }
            // Bound request goes through the real Bearer filter; JPA authorization first sees the
            // old managed entity, then refresh under the lock must expose the committed B scope.
            try {
                mvc.perform(put(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                    .content(body.replace("revision\":1", "revision\":2"))).andExpect(status().isForbidden());
            } catch (Exception failure) { throw new RuntimeException(failure); }
            status.setRollbackOnly();
        });
        jdbc.update("update stores set organization_id=? where id=?",a,store);
        assertThat(jdbc.queryForObject("select config_revision from store_merchant_configurations where store_id=?", Long.class,store)).isEqualTo(2);
    }
    private long organization(JdbcTemplate jdbc, String code) { return jdbc.queryForObject("insert into organizations(name,code,status) values (?,?,'active') returning id",Long.class,code,code); }
}
