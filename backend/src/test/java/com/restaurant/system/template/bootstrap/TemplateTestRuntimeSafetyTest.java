package com.restaurant.system.template.bootstrap;
import static org.junit.jupiter.api.Assertions.*;
import com.restaurant.system.printing.PrintingRuntimePolicyProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class TemplateTestRuntimeSafetyTest {
 private MockEnvironment env;
 private PrintingRuntimePolicyProperties policy;
 @BeforeEach void setup(){env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://db:5432/restaurant_template_test")
  .withProperty("spring.datasource.username","template_test").withProperty("app.environment","test");
  policy=new PrintingRuntimePolicyProperties();policy.setAllowedModes(List.of("MOCK","DISABLED"));policy.setEndpointConfigurationEnabled(false);}
 @Test void isolatedProfileIsAcceptedBeforeMigration(){assertDoesNotThrow(()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void foreignDatabaseIsRejectedBeforeMigration(){env.setProperty("spring.datasource.url","jdbc:postgresql://other:5432/foreign");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void wrongUserIsRejectedBeforeMigration(){env.setProperty("spring.datasource.username","other");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void alternateFlywayDatasourceIsRejected(){env.setProperty("spring.flyway.url","jdbc:postgresql://other:5432/foreign");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void realPrintingIsRejectedBeforeMigration(){policy.setAllowedModes(List.of("MOCK","DISABLED","REAL"));assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void endpointsAreRejectedBeforeMigration(){policy.setEndpointConfigurationEnabled(true);assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void externalWorkerIsRejectedBeforeMigration(){env.setProperty("UBER_EATS_WORKER_ENABLED","true");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void alloHasItsOwnExactDatabaseAndUser(){env.setActiveProfiles("allo-test");env.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:25442/restaurant_allo_nouilles_test");env.setProperty("spring.datasource.username","allo_test");assertDoesNotThrow(()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void alloCannotUseTemplateDatabase(){env.setActiveProfiles("allo-test");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void templateCannotUseAlloDatabase(){env.setActiveProfiles("template-test");env.setProperty("spring.datasource.url","jdbc:postgresql://db:5432/restaurant_allo_nouilles_test");env.setProperty("spring.datasource.username","allo_test");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void combinedTestProfilesAreRejected(){env.setActiveProfiles("allo-test","template-test");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
}
