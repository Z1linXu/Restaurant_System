package com.restaurant.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.bootstrap.ProductionAdminBootstrapApplication;
import com.restaurant.system.common.config.RuntimeDataSeeder;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.station.repository.StationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.repository.Repository;

class RestaurantSystemApplicationContextTest {

    @Test
    void normalCloudWebContextLoadsFullRepositoryScanAndRuntimeDataSeeder() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(RestaurantSystemApplication.class)
            .web(WebApplicationType.SERVLET)
            .run(cloudContextArgs())) {
            assertEquals(1, context.getBeanNamesForType(StationRepository.class).length);
            assertEquals(1, context.getBeanNamesForType(MenuItemRepository.class).length);
            assertEquals(1, context.getBeanNamesForType(PrinterConfigRepository.class).length);
            assertEquals(1, context.getBeanNamesForType(RuntimeDataSeeder.class).length);
            assertEquals(0, context.getBeanNamesForType(ProductionAdminBootstrapApplication.class).length);
            assertTrue(context.getBeanNamesForType(Repository.class).length >= 39);
        }
    }

    private String[] cloudContextArgs() {
        return new String[] {
            "--spring.profiles.active=cloud",
            "--server.port=0",
            "--spring.datasource.url=jdbc:h2:mem:cloud_context_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "--spring.datasource.driver-class-name=org.h2.Driver",
            "--spring.datasource.username=sa",
            "--spring.datasource.password=",
            "--spring.jpa.hibernate.ddl-auto=none",
            "--spring.jpa.show-sql=false",
            "--spring.flyway.enabled=true",
            "--spring.flyway.locations=classpath:cloud-context-empty-migrations",
            "--spring.sql.init.mode=never",
            "--spring.main.banner-mode=off",
            "--logging.level.root=ERROR",
            "--app.auth.jwt-secret=cloud-test-secret-123456789012345678901234567890",
            "--app.auth.x-user-id-fallback-enabled=false",
            "--app.dev-tools.role-switcher-enabled=false",
            "--app.seed.runtime-enabled=false",
            "--app.seed.force-overwrite=false",
            "--app.seed.safe-metadata-enabled=false",
            "--app.seed.default-users-enabled=false",
            "--app.seed.demo-data-enabled=false",
            "--app.seed.membership-supplement-enabled=false",
            "--app.seed.production-bootstrap-enabled=false",
            "--app.bootstrap-admin.enabled=false"
        };
    }
}
