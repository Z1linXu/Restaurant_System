package com.restaurant.system.bootstrap;

import com.restaurant.system.common.exception.BusinessException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProductionAdminBootstrapRunner implements ApplicationRunner {

    private final ProductionAdminBootstrapService bootstrapService;
    private final boolean enabled;

    public ProductionAdminBootstrapRunner(
        ProductionAdminBootstrapService bootstrapService,
        @Value("${app.bootstrap-admin.enabled:false}") boolean enabled
    ) {
        this.bootstrapService = bootstrapService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            return;
        }

        ProductionAdminBootstrapRequest request = readRequest();
        ProductionAdminBootstrapResult result = bootstrapService.bootstrap(request);
        if (result.dryRun()) {
            System.out.println("organization: " + result.organizationName());
            System.out.println("store: " + result.storeName());
            System.out.println("username: " + result.username());
            System.out.println("bootstrap dry-run success");
            return;
        }
        System.out.println("organization: " + result.organizationName());
        System.out.println("store: " + result.storeName());
        System.out.println("username: " + result.username());
        System.out.println("bootstrap success");
    }

    private ProductionAdminBootstrapRequest readRequest() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String mode = readRequiredLine(reader, "mode");
        boolean dryRun = "dry-run".equalsIgnoreCase(mode) || "validate".equalsIgnoreCase(mode);
        if (!dryRun && !"apply".equalsIgnoreCase(mode)) {
            throw new BusinessException("Unsupported bootstrap mode");
        }
        return new ProductionAdminBootstrapRequest(
            readRequiredLine(reader, "organization name"),
            readRequiredLine(reader, "store name"),
            readRequiredLine(reader, "owner username"),
            readRequiredLine(reader, "owner full name"),
            reader.readLine(),
            readRequiredLine(reader, "owner password"),
            dryRun
        );
    }

    private String readRequiredLine(BufferedReader reader, String label) throws IOException {
        String value = reader.readLine();
        if (value == null) {
            throw new BusinessException("Missing bootstrap input: " + label);
        }
        return value;
    }
}
