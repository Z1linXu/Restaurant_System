package com.restaurant.system.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class ProductionAdminBootstrapApplicationTest {

    @Test
    void nonWebBootstrapContextStartsForDryRun() {
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String input = String.join("\n",
            "dry-run",
            "Context Test Org",
            "Context Test Store",
            "owner",
            "Owner User",
            "",
            "StrongPass123!"
        ) + "\n";

        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        String databaseUrl = "jdbc:h2:mem:bootstrap_context_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ProductionAdminBootstrapApplication.class)
            .web(WebApplicationType.NONE)
            .run(
                "--spring.profiles.active=test",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.jpa.show-sql=false",
                "--spring.flyway.enabled=false",
                "--spring.main.banner-mode=off",
                "--logging.level.root=ERROR",
                "--app.bootstrap-admin.enabled=true",
                "--app.seed.default-users-enabled=false",
                "--app.seed.demo-data-enabled=false",
                "--app.seed.membership-supplement-enabled=false",
                "--app.seed.production-bootstrap-enabled=false"
            )) {
            assertNotNull(context.getBean(ProductionAdminBootstrapService.class));
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("bootstrap dry-run success"));
    }

    @Test
    void bootstrapAdminScriptSelfTestPasses() throws Exception {
        Path script = Path.of("..", "deployment", "cloud", "bootstrap-admin.sh");
        Process process;
        try {
            process = new ProcessBuilder("bash", script.toString(), "--self-test")
                .directory(Path.of(".").toFile())
                .redirectErrorStream(true)
                .start();
        } catch (IOException ex) {
            Assumptions.abort("bash is not available for deployment script self-test: " + ex.getMessage());
            return;
        }

        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(finished, "bootstrap-admin.sh --self-test timed out");
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("bootstrap-admin.sh self-test success"));
    }
}
