package com.restaurant.system;

import com.restaurant.system.bootstrap.ProductionAdminBootstrapApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.restaurant.system",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = ProductionAdminBootstrapApplication.class
    )
)
@MapperScan("com.restaurant.system.**.mapper")
@EnableScheduling
public class RestaurantSystemApplication {

    public static void main(String[] args) {
        if (bootstrapAdminEnabled(args)) {
            SpringApplication application = new SpringApplication(ProductionAdminBootstrapApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.run(args);
            return;
        }
        SpringApplication.run(RestaurantSystemApplication.class, args);
    }

    private static boolean bootstrapAdminEnabled(String[] args) {
        for (String arg : args) {
            if ("--app.bootstrap-admin.enabled=true".equalsIgnoreCase(arg)
                || "--app.bootstrap-admin.enabled".equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return "true".equalsIgnoreCase(System.getenv("APP_BOOTSTRAP_ADMIN_ENABLED"));
    }
}
