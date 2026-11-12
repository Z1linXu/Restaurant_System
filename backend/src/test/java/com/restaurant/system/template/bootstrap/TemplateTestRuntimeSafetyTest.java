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
 private void controlledProduction(){
  env.setActiveProfiles("allo-test");env.setProperty("spring.datasource.url","jdbc:postgresql://db:5432/restaurant_allo_nouilles_test");
  env.setProperty("spring.datasource.username","allo_test");env.setProperty("app.environment","production");
  env.setProperty("app.delivery.allo-production-printing-enabled","true");
  env.setProperty("app.delivery.allo-bootstrap-enabled","false");env.setProperty("app.template.test-bootstrap.enabled","false");
  for(String flag:List.of("runtime-enabled","force-overwrite","safe-metadata-enabled","default-users-enabled","demo-data-enabled","membership-supplement-enabled","production-bootstrap-enabled"))env.setProperty("app.seed."+flag,"false");
  policy.setAllowedModes(List.of("DISABLED","MOCK","PAD_DIRECT"));policy.setEndpointConfigurationEnabled(true);
 }
 @Test void explicitControlledAlloProductionAcceptsPadWithoutReal(){controlledProduction();assertDoesNotThrow(()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void exactIsolatedLocalQaIdentityCanExerciseTheSamePolicy(){controlledProduction();env.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:25442/restaurant_allo_nouilles_test");assertDoesNotThrow(()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void localQaCannotSubstituteAnotherPortOrDatabase(){controlledProduction();env.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:25443/restaurant_allo_nouilles_test");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));env.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:25442/restaurant_pos");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void productionPolicyCannotBeOpenedWithoutExplicitFlag(){controlledProduction();env.setProperty("app.delivery.allo-production-printing-enabled","false");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void productionFlagCannotBroadenGenericTemplateProfile(){controlledProduction();env.setActiveProfiles("template-test");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void productionFlagCannotBroadenTestEnvironment(){controlledProduction();env.setProperty("app.environment","test");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void realRemainsForbiddenWithProductionOptIn(){controlledProduction();policy.setAllowedModes(List.of("DISABLED","MOCK","PAD_DIRECT","REAL"));assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void productionBootstrapAndSeedersRemainForbidden(){controlledProduction();env.setProperty("app.delivery.allo-bootstrap-enabled","true");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));env.setProperty("app.delivery.allo-bootstrap-enabled","false");env.setProperty("app.seed.default-users-enabled","true");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void productionCannotSubstituteDatabaseIdentity(){controlledProduction();env.setProperty("spring.datasource.url","jdbc:postgresql://db:5432/restaurant_pos");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void productionCannotEnableExternalWorkers(){controlledProduction();env.setProperty("UBER_EATS_ENABLED","true");assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));}
 @Test void newIsolatedQaProfileCanBootstrapItsOwnExactLoopbackDatabase(){
  env.setActiveProfiles("allo-test","allo-qa");env.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:26512/restaurant_allo_nouilles_test");env.setProperty("spring.datasource.username","allo_test");
  assertDoesNotThrow(()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));
 }
 @Test void newIsolatedQaProfileCanExerciseProductionPrintingAfterBootstrapIsDisabled(){
  controlledProduction();env.setActiveProfiles("allo-test","allo-qa");env.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:26512/restaurant_allo_nouilles_test");
  assertDoesNotThrow(()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));
 }
 @Test void qaLoopbackRequiresExactProfilesAndPort(){
  controlledProduction();env.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:26512/restaurant_allo_nouilles_test");
  assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));
  env.setActiveProfiles("allo-test","allo-qa");env.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:26513/restaurant_allo_nouilles_test");
  assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));
  env.setProperty("spring.datasource.url","jdbc:postgresql://db:5432/restaurant_allo_nouilles_test");
  assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));
  env.setActiveProfiles("allo-qa");env.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:26512/restaurant_allo_nouilles_test");
  assertThrows(IllegalStateException.class,()->TemplateTestRuntimeSafety.validateBeforeMigration(env,policy));
 }
}
